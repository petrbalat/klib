# Analýza commitu `f3053984` (`feat(process)`) a návrhy na refaktoring

## 1. Nalezené chyby a rizika

### A. Ztráta `CancellationException` při chybě v `runProcess.kt`
- **Popis problému:** V bloku `catch (e: Throwable)` v `runProcess.kt` je voláno:
  ```kotlin
  } catch (e: Throwable) {
      process.stopGracefully(Duration.ZERO)
      killByEnv(ManagedProcess.INSTANCE_ENV) { it == instance }
      throw e
  }
  ```
  Pokud `killByEnv` (např. při čtení `/proc`) nebo `stopGracefully` vyhodí výjimku, původní výjimka `e` (zejména `CancellationException`) se potlačí a neproběhne správná propagace zrušení v korutinách.
- **Doporučená oprava:** Úklidové operace obalit do `withContext(NonCancellable)` a chránit pomocné volání pomocí `runCatching`, aby byla garantována re-throw původní výjimky `e`.

### B. Neřízená globální `CoroutineScope` pro roury v `runProcess.kt`
- **Popis problému:** Top-level scope `pipes`:
  ```kotlin
  private val pipes = CoroutineScope(
      SupervisorJob() + virtualThreadDispatcher +
          CoroutineExceptionHandler { _, e -> logger.debug("runProcess pipe failed", e) },
  )
  ```
  Job pro zápis stdin (`pipes.launch { writeStdin(process, stdin) }`) běží bez vazby na volající korutinu a při timeoutu/chybě procesu může pokračovat v běhu zbytečně dlouho.
- **Doporučená oprava:** Ukládat referenci na `stdinJob` a při timeoutu či selhání procesu jej explicitně zrušit (`stdinJob.cancel()`).

### C. Možný deadlock a race condition při rychlém zastavení v `ProcessGroup`
- **Popis problému:**
    - V `ProcessGroup.start()` je proces spuštěn přes `process.start()`, kde se `watchdog` korutina vytváří asynchronně přes `scope.launch`.
    - Pokud mezitím jiné vlákno nebo callback zavolá `group.stop()`, projde přes `processes.map { launch { it.stop() } }.joinAll()`. Pokud `watchdog` ještě nebyl přiřazen, `ManagedProcess.stop()` mine běžící proces.
    - Listener `gaveUp` smí synchronně volat `group.stop()` (i přes `runBlocking`), což v kombinaci se synchronizací `synchronized(lock)` může při nevhodném propletení zablokovat vlákno watchdogu.
- **Doporučená oprava:** Zabezpečit nastavení `watchdog` Jobu přímo při startu před publikováním stavu a ověřit korektní chování zámků při reentrantním volání `stop()`.

### D. Ověření existence skupiny v `ProcessGroups.create`
- **Popis problému:** Instance `ProcessGroup` se vytváří před kontrolou, zda již skupina s daným ID neexistuje v `_groups`:
  ```kotlin
  val group = ProcessGroup(id, tagging, scope, reaper, defaults, listener)
  _groups.update {
      require(id !in it) { "Process group $id already exists" }
      it + (id to group)
  }
  ```
- **Doporučená oprava:** Zkontrolovat existenci klíče před inicializací nebo zamezit zbytečné alokaci zdrojů před validací.

---

## 2. Návrhy na refaktoring a zjednodušení kódu

### 1. Robustnější čtení souboru `/proc/<pid>/environ` v `ProcessReaper.kt`
- Zkontrolovat existenci a čitelnost souboru (`canRead()`) před čtením, zachytit případné `IOException` a vyhnout se zbytečné alokaci prázdných bufferů.

### 2. Sjednocení úklidu procesu v `runProcess.kt`
- Sjednotit ošetření ukončení procesu při timeoutu i při výjimce do přehledného bloku s garantovaným `NonCancellable` kontextem, kde se bezpečně dorazí visící potomci podle `INSTANCE_ENV`.

### 3. Zjednodušení vyhodnocování restartů v `ManagedProcess.watch`
- Extrahovat vyhodnocení resetu počítadla po `stableRun` do samostatné pomocné funkce pro snížení kognitivní složitosti smyčky `while (true)`.
