# Refaktoring `spring-boot-process`: analýza commitu `f3053984` a návrh Kotlin DSL

> Je to návrh, nic z toho zatím není implementované. Cesty jsou relativně k
> `spring-boot-process/src/main/kotlin/cz/petrbalat/klib/process/` a čísla řádků platí pro commit `f3053984`.
> Vstupem byla mimo jiné analýza v [`process-fix-refac.md`](process-fix-refac.md).

## Stav implementace (2026-10-10)

Implementováno ve větvi `feature/spring-boot-process`, po commitech (každý se zelenými `test` a `dockerTest`):

| Commit | Body návrhu |
|---|---|
| `fix(process): ManagedProcess — každé spuštění watchdogu s vlastním stavem` | 2.1 |
| `fix(process): runProcess — timeout s jistým návratem, strop výstupu, jednotný úklid` | 1/A, 1/B (jen zavření stdin), 1/2, 2.2, 2.3 |
| `fix(process): IntervalScheduler — čekání odolné proti skoku hodin a letnímu času` | 2.4, 2.7 |
| `fix(process): název procesu je ve skupině klíčem, cyklus nejde spustit dvakrát` | 2.9, 2.11 (dvojí start) |
| `refactor(process): logika restartů watchdogu v RestartBudget` | 1/3, 3.3 |
| `refactor(process): ProcessSpec — jeden neměnný popis procesu` | 3.1, launcher z 7.4 |
| `refactor(process): registr ProcessGroups v jádru, Spring jen publikuje eventy` | 3.4, 2.6 (hláška) |
| `feat(process): Kotlin DSL pro popis procesu, skupinu a registr` | 4.1–4.4, 2.6 (`withGroup` počká) |
| `feat(process): DSL pro runProcess, checkSuccess a příkaz s tagem skupiny` | 4.5 |
| `feat(process): DSL segmentů, nowPlaying a cyklus navázaný na skupinu` | 4.6, 2.5, 2.11 (runtime stav) |
| `feat(process): DSL pro IntervalScheduler a důvod konce intervalu (EndReason)` | 4.7, 2.8 |

Odchylky od návrhu:
- Launcher (`processBuilder()` v `ProcessSpec.kt`) vrací jen `ProcessBuilder`, bez `signalTarget` — přidá se až
  s izolací (kap. 7), dnes by byl vždy stejný jako proces.
- Autokonfigurace staví `ProcessGroups` konstruktorem (kvůli přepsatelným beanům `ProcessTagging` / `ProcessReaper`),
  DSL `processGroups(owner) { }` je pro použití bez Springu.
- Místo `group.cyclePlayer()` má `SegmentCyclePlayer` volitelný parametr `group` (tag + navázání na `stop()` skupiny);
  `group.playCycle { }` je strukturovaná obálka nad ním. `stop()` skupiny zevnitř cyklu (`whilePlaying`) na cyklus
  nečeká — kontext `GroupWork`.
- `runProcess` DSL bez `stdin(file)`, `charset` a `args { }` — podmíněné argumenty přes `runProcess(command("x") { … })`.
- `IntervalScheduler.schedule` s parametry (tři lambdy) odstraněn, zůstalo jen DSL.
- `ProcessGroup.start(spec)` nedosazuje `ProcessDefaults` — výchozí hodnoty `klib.process.*` dosazuje DSL `start(name) { }`.

Neimplementováno (volitelné v kap. 6 bod 6): rotace logu za běhu (2.10), `StopStrategy.WholeTree` (2.12), backoff
restartů, tag `name` u Micrometer počítadel, `ProcessOutput.Inherit`, izolace přes PID namespace (kap. 7).

## 0. Shrnutí

- Modul je zatím jen ve větvi `feature/spring-boot-process`, na `master` ještě není. Rozhraní se proto dá měnit bez
  ohledu na zpětnou kompatibilitu. Pozdější změny API by vyšly dráž.
- Z `process-fix-refac.md` dávají smysl body **2** (sjednocení úklidu v `runProcess`) a **3** (vyčlenění logiky
  restartů, ale do samostatné třídy, ne jen do privátní funkce). Bod **A** stojí za to jen jako dvouřádková pojistka
  v rámci bodu 2. Body **B, C, D a 1** stojí na chybném předpokladu nebo nemají žádný dopad (kap. 1).
- Vlastní analýza našla skutečné problémy na jiných místech (kap. 2):
  - `ManagedProcess` sdílí stav mezi jednotlivými spuštěními. Starý watchdog pak může zabít nový proces.
  - `runProcess` po timeoutu volá `waitFor()` bez limitu a výstup drží v paměti bez stropu.
  - `IntervalScheduler` čeká na začátek a konec intervalu jediným `delay`. Na zařízení bez RTC ho rozhodí skok
    hodin po NTP synchronizaci, jinde přechod na letní čas.
  - Cyklus segmentů není svázaný se životností skupiny a po jejím `stop()` spouští další procesy.
- Hlavní refaktoring jádra (kap. 3):
  - jeden neměnný popis procesu `ProcessSpec`,
  - „inkarnace“ (jednotlivá spuštění) watchdogu v `ManagedProcess`,
  - čistá logika restartů `RestartBudget`,
  - registr skupin bez Springu, ze Springu zbude jen adaptér.
- DSL (kap. 4): `processSpec {}`, `group.start("mpv") {}`, `processGroups.withGroup(id) {}`, `runProcess("cmd") {}`,
  `segment(duration) {}` + `group.playCycle {}` a `scheduler.schedule(id, start, end) { onStart {}; onEnd {} }`.
- Postup (kap. 6): nejdřív opravy bez změny API, každou samostatně s testem. Potom `ProcessSpec` a DSL.
- Izolace přes PID namespace (`unshare`, kap. 7) je ověřená v kontejneru. Funguje jen jako *přídavná* vrstva na
  Linuxu (userns varianta, s `sudo` nefunguje) a tagy nenahradí. Doporučení je nedělat ji před refaktoringem, ale
  rovnou na ni připravit interní launcher (krok 3).

---

## 1. Vyhodnocení návrhů z `process-fix-refac.md`

| # | Návrh | Verdikt |
|---|---|---|
| A | `NonCancellable` + `runCatching` v `catch` v `runProcess` | Jen částečně, nízká priorita, a jako součást bodu 2 |
| B | Rušit `stdinJob` při timeoutu nebo chybě | Ne. Zrušení blokující zápis nepřeruší |
| C | Race mezi `start()` a `stop()` skupiny, deadlock s listenerem `gaveUp` | Ne pro `ProcessGroup`. Skutečný problém je v `ManagedProcess` (2.1) |
| D | Kontrolovat id před vytvořením `ProcessGroup` | Ne. Nemá dopad. Skutečný problém `create` je jinde (2.6) |
| 1 | Robustnější čtení `/proc/<pid>/environ` | Ne |
| 2 | Sjednotit úklid v `runProcess` | **Ano** |
| 3 | Vyčlenit nulování restartů po `stableRun` | **Ano**, ale jako třída `RestartBudget` (3.3) |

### A: ztráta `CancellationException` v `runProcess`

- Úklid v `catch` (`runProcess.kt:60-65`) nemá žádný suspendovací bod: `stopGracefully` i `killByEnv` jsou blokující
  funkce. `withContext(NonCancellable)` ovlivňuje jen suspend volání, takže by tu nic nezměnil. Zrušení korutiny
  blokující kód nepřeruší.
- Žádné z těch volání prakticky nevyhodí:
  - `stopGracefully` volá `descendants()`, `destroy()` a `waitFor(timeout)`. `InterruptedException` hrozí jen při
    přerušení vlákna a korutiny na `Dispatchers.IO` vlákna nepřerušují.
  - `killByEnv` používá `listFiles().orEmpty()`, `readProcessTag` chytá `IOException` a `killer` je obalený
    v `runCatching`.

  Původní výjimka `e` se tedy neztrácí.
- Smysl má jen jako levná pojistka: `runCatching { terminate() }.exceptionOrNull()?.let(e::addSuppressed)`. Výjimku
  úklidu nezahodí, jen ji připojí k původní. Patří do bodu 2.

### B: neřízený scope `pipes`, rušení `stdinJob`

- Scope `pipes` je mimo strukturu volajícího záměrně (KDoc `runProcess.kt:79-82`). `withContext` by jinak čekal na
  dočtení roury, kterou drží potomek. Globální scope je tu záměr, ne chyba.
- `stdinJob.cancel()` nepomůže. Job visí v blokujícím `write` do roury a zrušení korutiny blokující I/O nepřeruší.
  Nepomůže ani `runInterruptible`, protože `FileOutputStream` není `InterruptibleChannel`. Zápis skončí, až se zavře
  čtecí konec roury, tedy až zemřou všechny procesy, které drží stdin. `Process.destroy()` (volá ho
  `stopGracefully`) navíc zavře konce rour na straně JVM.
- „Běží zbytečně dlouho“ nastane jen ve chvíli, kdy stdin zdědil potomek, nečte ho a stdin je větší než 64 KB. Vlákno
  je virtuální, takže to stojí zanedbatelně, a skončí po zabití potomka.
- Co smysl má: při `stdin == null` nic nespouštět a jen synchronně zavolat `process.outputStream.close()`. Zavření
  neblokuje a ušetří jedno virtuální vlákno na volání. Skutečné problémy `runProcess` jsou v 2.2 a 2.3.

### C: race a deadlock v `ProcessGroup`

- Tvrzení, že se `watchdog` přiřazuje asynchronně, neplatí.
  - `scope.launch` vrací `Job` synchronně. Přiřazení `watchdog = …` (`ManagedProcess.kt:128`) proběhne dřív, než
    `start()` skončí. S `UNDISPATCHED` dokonce tělo běží na volajícím vlákně až do prvního suspendu.
  - `ProcessGroup.start` celé tohle dělá pod `lock` (`ProcessGroup.kt:90-112`). `stop()` projde stejným zámkem dřív,
    než začne číst `processes` (`:120-123`), takže watchdog vždycky najde.
  - Přesně tohle pokrývá test `souběžný start a stop skupiny nenechá běžet žádný proces` (100 kol).
- Ani deadlock s listenerem `gaveUp` nehrozí.
  - `stop()` drží zámek jen po dobu přepnutí příznaku, nikdy ne během čekání.
  - Na watchdog, který to vzdal, se nečeká (příznak `gaveUp`, `ManagedProcess.kt:219-221`), takže nečeká sám na sebe.
  - Monitor je reentrantní, takže nevadí ani volání ze stejného vlákna.
  - Pokrývá to test `listener ProcessGaveUpEvent smí skupinu synchronně zastavit`.
- Co je pravda: samotný `ManagedProcess` (bez `ProcessGroup`) není vláknově bezpečný pro `start`/`stop` a sdílí stav
  mezi spuštěními. Opravovat je potřeba tam (2.1), ne ve skupině.
- Jedna drobnost v `ProcessGroup` skutečně je: `processes += process` stojí před `process.start()`
  (`ProcessGroup.kt:109-110`). Když první spuštění selže (chybí binárka), zůstane v seznamu mrtvý `ManagedProcess`
  až do `stop()`. Stačí prohodit pořadí (viz 2.9).

### D: vytváření `ProcessGroup` před kontrolou id

- Konstruktor `ProcessGroup` nemá vedlejší efekty. Vytvoří jen pole, `MutableStateFlow` a mapu s tagem a žádný
  prostředek nealokuje.
- Vytvářet skupinu uvnitř `_groups.update { }` by bylo horší. `update` je CAS smyčka a lambda může proběhnout víckrát.
- Skutečný problém v `create` je jinde: race s asynchronním `stop()` po skončení scope (2.6).

### 1: čtení `/proc/<pid>/environ`

- `readProcessTag` už `IOException` chytá (`ProcessReaper.kt:64-68`). To pokrývá neexistující soubor (proces mezitím
  skončil), EACCES (proces jiného uživatele) i ESRCH.
- Kontrola `canRead()` před čtením je TOCTOU (proces může skončit mezi kontrolou a čtením) a zdvojuje to, co už
  ošetřuje výjimka.
- Alokace nehrají roli. Kotlin `File.readBytes()` správně čte i soubory v `/proc` s nulovou délkou, `environ` má
  jednotky KB a sken `/proc` běží jen při `stop()` skupiny nebo restartu, tedy mimo horkou cestu.

### 2: sjednocení úklidu v `runProcess` (ano)

- Dnes se timeout a zrušení chovají každé jinak:
  - Timeout zabije jen strom (`:51`). `killByEnv` se zavolá jen tehdy, když po `OUTPUT_GRACE` někdo pořád drží rouru
    (`:53-57`).
  - Výjimka i zrušení zabijí strom a navíc zavolají `killByEnv` (`:62-63`).

  Démon po double-forku (není ve stromu a rouru nedrží) proto přežije timeout, ale ne zrušení. To odporuje KDoc
  „Timeout i zrušení korutiny zabijí proces i jeho potomky“.
- Návrh:
  - Jedna lokální funkce `terminate()` = `stopGracefully(Duration.ZERO)` + `killByEnv(INSTANCE_ENV)`.
  - Volá se při timeoutu i v `catch`. V `catch` se případná výjimka úklidu připojí přes `addSuppressed` (tím je
    vyřízený i bod A).
  - `NonCancellable` jen kolem úklidu, který suspenduje (`joinWithin`), tak jako dnes.

### 3: vyčlenění nulování po `stableRun` (ano, jinak)

Privátní pomocná funkce ve stejné třídě by byla jen kosmetika. Smysl má malá čistá třída `RestartBudget` (3.3).
Oddělí rozhodnutí „restartovat / vzdát se / vynulovat“ od procesů a času, takže ji půjde testovat jednotkově bez
reálných procesů a `sleep`. Dnešní test `pád po stabilním běhu nuluje počítadlo a restartuje dál` potřebuje skutečné
procesy. Je to i přirozené místo pro případný backoff.

---

## 2. Problémy z vlastní analýzy

### 2.1 `ManagedProcess`: sdílený stav mezi spuštěními (závažnost: střední)

`ManagedProcess.kt:105-118, 123-131, 156-164`. Pole `process`, `instance`, `gaveUp` a `watchdog` patří objektu, ale
logicky patří k jednomu běhu watchdogu. `finally` starého watchdogu je čte až ve chvíli, kdy je mohl nový `start()`
přepsat.

- **Scénář 1 (deterministický).** `onStateChange` na `GaveUp` zavolá `start()`. Může to být automatický nový pokus
  nebo synchronně obsloužené tlačítko „Zkusit znovu“, KDoc to nezakazuje. Pořadí:
  1. `giveUp()` nastaví `gaveUp = true` a zavolá `onStateChange(GaveUp)`.
  2. Uvnitř `start()` nastaví `gaveUp = false`, `process = P2` a stav `Running`.
  3. Po návratu do `setState` se zapíše `_state.value = GaveUp`, takže přepíše `Running`.
  4. `finally` starého watchdogu zavolá `process?.stopGracefully()`, a tím **zabije P2**. Protože `gaveUp == false`,
     zavolá i `killLeftovers()`, které zabije potomky P2. Nakonec nastaví `process = null` a `setState(Stopped)`.
  5. Nový watchdog uvidí konec P2 a restartuje ho. Výsledkem je špatný stav a zbytečně zabitý proces.
- **Scénář 2 (race).** Totéž z jiného vlákna hned poté, co volající ve `state` uvidí `GaveUp`. `finally` starého
  watchdogu běží až po `_state.value = GaveUp`. Test `po GaveUp jde proces znovu spustit` prochází jen proto, že
  vlákno watchdogu doběhne `finally` dřív, než se probudí testovací vlákno. Garantované to není.
- **Scénář 3.** `start()` na běžícím procesu nemá žádnou pojistku.
  - `killLeftovers()` zabije *běžící* proces, protože nese aktuální `INSTANCE_ENV`.
  - Vznikne druhý watchdog a přepíše `watchdog`, takže první už `stop()` nezastaví (jen zrušení scope).
  - Oba watchdogy pak zapisují do sdíleného `process`.
- **Scénář 4.** Souběžný `start()` a `stop()` z různých vláken. `stop()` může přečíst `watchdog == null` nebo starý
  job a skončit, zatímco se nový proces právě spouští. `ProcessGroup` to serializuje zámkem a `SegmentCyclePlayer`
  volá sekvenčně, takže se to týká přímého použití.

**Oprava bez změny API:** zavést inkarnaci `Run` s vlastním `process`, `instance`, `gaveUp` a `job` (3.2).
- `finally` pracuje jen se svou inkarnací.
- Stav zapisuje jen aktuální inkarnace.
- `start()` běží pod zámkem a kontroluje `check(current?.isActive != true)`.

### 2.2 `runProcess`: timeout negarantuje návrat (závažnost: střední)

`runProcess.kt:59` volá `process.waitFor()` bez limitu. Po timeoutu `stopGracefully(ZERO)` pošle SIGKILL a čeká
nejvýš 2 s (`processStop.kt:28`).

- Proces v nepřerušitelném spánku (stav D) SIGKILL neukončí, dokud se nevrátí jeho I/O. Typicky jde o zaseknutý
  NFS/CIFS mount nebo odpojený USB disk, tedy přesně „spadlý síťový mount“, se kterým modul už počítá
  v `SegmentCyclePlayer`.
- `waitFor()` pak blokuje navždy a zrušení korutiny nepomůže, protože jde o blokující volání. Takže
  `runProcess(listOf("ls", "/mnt/share"), timeout = 5.seconds)` může viset donekonečna.
- **Oprava:**
  - Po timeoutu brát exit kód jen s limitem: `withTimeoutOrNull(limit) { process.onExit().await() }`.
  - Když se ho nedočká, vrátit výsledek s `exitValue = null`. To je změna rozhraní (4.5). Proces dorazí jádro, až se
    I/O vrátí. Vlákno čtení roury doběhne samo a je levné (virtuální).

### 2.3 `runProcess`: výstup bez stropu v paměti (závažnost: střední)

`StringWriter` (`:46`) sbírá celý výstup. Upovídaný proces (debug log, `yes`, nekonečná smyčka) za 30 s timeoutu
vyrobí stovky MB. Na kiosku s malou haldou to končí OOM celé aplikace.

**Oprava:**
- Přidat `maxOutput` (výchozí třeba 1 M znaků) a držet **konec** výstupu, protože chyby bývají na konci. Kruhový
  buffer, případně začátek + konec.
- Ve výsledku nastavit příznak `outputTruncated`.
- Čtení pokračuje (zbytek se dočte a zahodí), aby se roura nezablokovala.

### 2.4 `IntervalScheduler`: jeden dlouhý `delay` podle wall-clock (závažnost: střední)

`delayUntil` (`IntervalScheduler.kt:251-256`) spočítá rozdíl jednou a zavolá `delay()`, který běží na monotónním
čase. Změna wall-clock po naplánování se do čekání nepromítne.

- **Skok NTP po bootu na zařízení bez RTC** (s tím modul sám počítá, viz KDoc `ManagedProcess.awaitExit`). Plán
  naplánovaný při startu s hodinami v roce 1970 čeká přibližně 56 let, takže interval nikdy nezačne. Opačný skok
  spustí interval okamžitě.
- **Letní čas.** `Duration.between(LocalDateTime, LocalDateTime)` ignoruje změnu offsetu. Interval na 03:30,
  naplánovaný v 01:30 v noci přechodu na letní čas, dostane spočítané 2 h, ale skutečně uplyne jen 1 h, takže začne
  o hodinu později. Na podzim začne o hodinu dřív. Totéž platí pro `end` a pro limit `awaitReady` (`:146`).

**Oprava:**
- Převádět `LocalDateTime` na `Instant` přes `clock.zone` (`atZone(zone).toInstant()`). Nejednoznačné a neexistující
  lokální časy se pak řeší podle pravidel `ZonedDateTime`, což je potřeba zdokumentovat.
- Čekat po krocích: `while (clock.instant() < target) delay(min(remaining, maxStep))`, s `maxStep` 30 s až 1 min.
- Test je snadný, protože třída už `clock` přijímá. V testu stačí měnitelný `Clock`: posun dopředu a interval musí
  začít do `maxStep`.

### 2.5 `SegmentCyclePlayer` není svázaný se životností `ProcessGroup` (závažnost: nízká až střední)

Procesy segmentů nesou tag skupiny jen tehdy, když konzument ručně předá `group.environment` do `Segment.environment`.

Když se skupina zastaví za běhu cyklu, cyklus pokračuje. Typický případ: listener `ProcessGaveUpEvent` jiného procesu
skupiny zavolá `group.stop()`.
1. `reaper.kill(groupId)` zabije aktuální proces segmentu.
2. Watchdog s `RestartPolicy.NEVER` ohlásí `GaveUp`.
3. Cyklus to vyhodnotí jako pád a **spustí další segment**, s tagem skupiny, i když je skupina už zastavená.

Skupina tomu zabránit nemůže, protože `isStopped` hlídá jen `ProcessGroup.start`.

**Oprava:** udělat cyklus součástí skupiny (`group.playCycle {}`, 4.6). `stop()` skupiny nejdřív zruší připojené
cykly a počká na ně, teprve potom zastaví procesy.

### 2.6 `ProcessGroups.create` po skončení scope hlásí falešné „already exists“ (závažnost: nízká až střední)

`stopOnScopeCompletion` (`ProcessGroup.kt:73-77`) spouští `stop()` asynchronně v novém `CoroutineScope(Dispatchers.IO)`.
Skupina zůstává v registru, dokud `stop()` nedoběhne (včetně skenu `/proc`).

Konzument, který spoléhá na automatický stop a hned znovu spustí akci se stejným id
(`job.cancelAndJoin(); groups.create("action-42", newScope)`), dostane
`IllegalArgumentException: Process group action-42 already exists`. `join()` totiž vrátí řízení dřív, než asynchronní
`stop()` doběhne.

**Oprava:**
- `withGroup` (4.3) zastavuje skupinu synchronně ve `finally`.
- `create` odliší skupinu, která se právě zastavuje (`isStopped == true`):
  - suspend varianta počká na její odebrání,
  - nesuspendující varianta vyhodí srozumitelnou chybu („… is still stopping“).

### 2.7 `IntervalScheduler`: `onEnd(replaced = true)` bez nástupce (závažnost: nízká)

`schedule()` nastaví `previous.replaced = true` a předchozí interval zruší (`:119-121`) ještě **před** kontrolou
`end <= now` (`:124-128`).

Když se nový plán přeskočí, protože jeho konec už uplynul (třeba zkrácení běžící akce do minulosti), starý interval
skončí s `onEnd(replaced = true)`, přestože ho nic nenahradí. KDoc přitom říká „akce se hned restartuje, konzument
nesmí plán značit jako definitivně ukončený“, takže konzument nechá plán navždy ve stavu „běží“.

**Oprava:** o přeskočení rozhodnout nejdřív. `replaced = true` nastavit jen tehdy, když opravdu vznikne nový job.
Jinak ukončit s důvodem `Cancelled` (4.7).

### 2.8 `IntervalScheduler`: rozporný kontrakt `onEnd` po selhání `onStart` (závažnost: nízká)

- KDoc třídy (`:32-34`) říká: „Pokud `onStart` proběhl (byť jen začal), `onEnd` se zavolá vždy.“
- KDoc `schedule` (`:95-99`) i kód říkají opak: výjimka nebo timeout v `onStart` znamená, že se `onEnd` nezavolá.

Když `onStart` už spustil proces a potom spadl nebo visel do `callbackTimeout` (30 s), proces běží dál bez úklidu.

**Doporučení:** volat `onEnd` i po selhání nebo timeoutu `onStart`, s důvodem `EndReason.StartFailed` (4.7). Úklid
bývá idempotentní a konzument ho může ignorovat. Minimálně je potřeba sjednotit KDoc.

### 2.9 `ProcessGroup`: seznam procesů a stav podle názvu (závažnost: nízká)

- `processes` jen roste (`ProcessGroup.kt:52, 109`). Dlouho žijící skupina (třeba celé aplikace s opakovaně
  spouštěnými pomocnými procesy) drží všechny skončené `ManagedProcess` až do `stop()` a `stop()` je všechny prochází.
- `_state` je klíčovaný podle `name`. Dva živé procesy se stejným názvem si stav přepisují. Stačí dva procesy `sh`
  bez explicitního názvu, protože výchozí název je `File(command.first()).name`.
- Selhané první spuštění zůstává v `processes` (1/C).

**Oprava:**
- Evidovat procesy podle názvu.
- Pokus o `start` se jménem *živého* procesu (`Running`, `Restarting`) vyhodí `IllegalStateException`.
- Procesy ve stavu `Stopped` nebo `GaveUp` se stejným názvem se nahradí. U `GaveUp` se nejdřív zavolá `stop()`, aby se
  dorazily pozůstatky.
- Do seznamu přidat proces až po úspěšném `start()`.
- Pozor, je to změna chování: dva nepojmenované procesy `sh` dnes projdou. Chybová hláška musí radit nastavit `name`.

### 2.10 Log se rotuje jen při (re)startu (závažnost: nízká)

`rotateLog` se volá jen v `launchProcess` (`ManagedProcess.kt:202`). Proces, který týdny běží bez pádu (Chromium na
kiosku), může log zvětšovat bez omezení. Na SD kartě je to opak toho, co slibuje `maxBytes`: disk nezaplní jen padající
proces, ale i upovídaný proces, který nepadá.

**Oprava (volitelná):**
- Watchdog bude během `awaitExit` periodicky (třeba jednou za minutu) kontrolovat velikost a dělat copy + truncate:
  zkopírovat do `<log>.1` a zavolat `FileChannel.truncate(0)`.
- S `Redirect.appendTo` to funguje, protože soubor má `O_APPEND` a proces po zkrácení píše na nový konec.
- Řádky zapsané mezi kopií a zkrácením se ztratí. U diagnostických logů je to přijatelné, ale musí to být
  zdokumentované.

### 2.11 `SegmentCyclePlayer`: dvojí start a runtime stav v popisu segmentu (závažnost: nízká)

- `start()` přepíše `cycleJob` bez kontroly (`SegmentCyclePlayer.kt:89`). Předchozí cyklus pak běží dál a
  `stopAndJoin()` ho nezastaví, takže hrají dva procesy současně a neplatí „vždy běží právě jeden proces“. KDoc sice
  říká „volající nejdřív volá stopAndJoin“, ale nic to nevynucuje. Řešení: `check(cycleJob?.isActive != true)`.
- `Segment` je popis (provider může každý cyklus vracet tytéž instance), ale nese měnitelné `process`. Runtime stav
  patří přehrávači (`nowPlaying`, 4.6).

### 2.12 `stopGracefully`: SIGTERM dostane jen rodič (závažnost: nízká, jde o design)

`processStop.kt:23` posílá SIGTERM jen kořenovému procesu. Potomci dostanou SIGKILL až po jeho smrti.

U wrapperů (`sh -c "mpv …"`, `soffice` → `soffice.bin`, spouštěcí skripty) wrapper na SIGTERM hned skončí a skutečný
program dostane SIGKILL bez šance uklidit. Zůstanou třeba lock soubory profilu LibreOffice, které pak konzument uklízí
v `Segment.prepare`.

**Návrh (volitelný):** přidat `StopStrategy.WholeTree` do `ProcessSpec`.
- SIGTERM rodiči i všem sesbíraným potomkům naráz.
- Počkat `gracefulStop`.
- SIGKILL těm, kdo přežili.
- Výchozí zůstane dnešní chování (`ParentFirst`), protože Chromium si renderery ukončuje samo.

### 2.13 Poznámky bez nutné akce

- **Watchdog s `UNDISPATCHED`** (`ManagedProcess.kt:128`) běží do prvního suspendu na volajícím vlákně.
  - V `ProcessGroup` to znamená pod zámkem skupiny.
  - Prakticky jde jen o `onExit().await()`, které suspenduje.
  - Tvrzení „eventy se publikují z vlákna watchdogu“ (CLAUDE.md, KDoc `ProcessGaveUpEvent`) ale není striktně
    zaručené. Pokud by `onExit()` už bylo dokončené, první `Restarting` nebo `GaveUp` by se publikoval z vlákna, které
    volá `start()`, a pod zámkem skupiny. Stačí to uvést v KDoc.
- **`sudo` resetuje prostředí** (`env_reset`). Proces pod `sudo` tak vůbec nenese `KLIB_PROCESS_TAG` ani
  `KLIB_PROCESS_INSTANCE`, nejde jen o nečitelný `environ`. README uvádí jen nečitelnost, je potřeba to doplnit.
  Promítá se to i do DSL `sudo()` (4.1).
- **`IntervalScheduler` míchá `java.time.Duration` a `kotlin.time.Duration`** (plně kvalifikovaný typ
  v konstruktoru). Ve veřejném API sjednotit na `kotlin.time`.
- **`rotateLog` leží v `processStop.kt`.** Patří k `ProcessOutput` (`processOutput.kt`).

---

## 3. Refaktoring jádra (bez DSL)

### 3.1 `ProcessSpec`: jeden popis procesu

Dnes se tytéž parametry opakují na čtyřech místech, pokaždé s jinými výchozími hodnotami a v jiném pořadí:

| | command | name | env | output | restart | gracefulStop | workingDirectory |
|---|---|---|---|---|---|---|---|
| `ManagedProcess(...)` | ano | ano | ano | výchozí `Discard` | ano | ano | chybí |
| `ProcessGroup.start(...)` | ano | ano | ano | **povinný** | ano | jen z defaults | chybí |
| `Segment(...)` | ano | chybí | ano | ano | pevně `NEVER` | chybí | chybí |
| `runProcess(...)` | ano | chybí | ano | (roura) | – | – | chybí |

Pracovní adresář (`ProcessBuilder.directory`) nejde nastavit nikde. Chybí třeba pro konverzi přes LibreOffice nebo pro
ffmpeg s relativními cestami.

```kotlin
/** Neměnný popis externího procesu — sdílí ho ManagedProcess, ProcessGroup i segmenty cyklu. */
data class ProcessSpec(
    /** seznam argumentů, bez shellu */
    val command: List<String>,
    /** do logů, názvu korutiny watchdogu, [GiveUp] a klíče ve stavu skupiny */
    val name: String = File(command.first()).name,
    val environment: Map<String, String> = emptyMap(),
    val workingDirectory: File? = null,
    val output: ProcessOutput = ProcessOutput.Discard,
    val restart: RestartPolicy = RestartPolicy(),
    /** SIGTERM → čekání → SIGKILL */
    val gracefulStop: Duration = 5.seconds,
    val stopStrategy: StopStrategy = StopStrategy.ParentFirst,
) {
    init {
        require(command.isNotEmpty()) { "command must not be empty" }
    }
}
```

Výchozí `name` z `command.first()` se vyhodnotí ještě před `init`. Prázdný seznam by proto spadl na
`NoSuchElementException` dřív, než ho chytí `require`. Validaci tedy udělá builder (4.2), případně sekundární
konstruktor.

Kde se `ProcessSpec` použije:
- `ManagedProcess(spec: ProcessSpec, scope: CoroutineScope, onStateChange: (ProcessState) -> Unit = {})`. Veřejné
  `val spec` nahradí dnešní veřejné `command`, `name` a `output`.
- `ProcessGroup.start(spec: ProcessSpec): ManagedProcess`. Skupina přidá do env svůj tag a ten má přednost, stejně
  jako dnes.
- `GiveUp(name, command, restarts, output)` se změní na `GiveUp(spec, restarts)`. `name` zůstane jako odvozená
  vlastnost.
- `Segment(duration, spec, prepare, whilePlaying)`.

Výchozí hodnoty skupiny (`ProcessDefaults`) dosazuje builder (4.2), ne `ProcessSpec`. Spec díky tomu nepotřebuje
nullable pole ve smyslu „zdědit z defaults“.

### 3.2 `ManagedProcess`: inkarnace

```kotlin
class ManagedProcess(
    val spec: ProcessSpec,
    private val scope: CoroutineScope,
    private val onStateChange: (ProcessState) -> Unit = {},
) {
    private val lock = Any()

    /** poslední spuštění; mění se jen pod [lock] */
    @Volatile
    private var current: Run? = null

    val state: StateFlow<ProcessState> = _state.asStateFlow()
    val process: Process? get() = current?.process
    val isAlive: Boolean get() = process?.isAlive == true

    /** Selhání prvního spuštění vyhodí výjimku; běžící proces → IllegalStateException. */
    fun start(): Unit = synchronized(lock) {
        val previous = current
        check(previous == null || !previous.isRunning) { "Process ${spec.name} is already running" }
        previous?.killLeftovers() // po GaveUp — pozůstatky posledního běhu by blokovaly zařízení / profil
        current = Run().also { it.launch() }
    }

    suspend fun stop() {
        val run = synchronized(lock) { current } ?: return
        run.stop()
    }

    /** stop + start — „Zkusit znovu“ po GaveUp */
    suspend fun restart() {
        stop()
        start()
    }

    /** Jedno spuštění watchdogu: vlastní proces, instance, příznak vzdání se a job. */
    private inner class Run {
        @Volatile var process: Process? = null
        @Volatile var instance: String? = null
        @Volatile var gaveUp = false
        lateinit var job: Job

        val isRunning: Boolean get() = job.isActive && !gaveUp

        /** zastaralá inkarnace (po novém start()) stav nehlásí */
        fun setState(state: ProcessState) {
            if (current !== this) return
            runCatching { onStateChange(state) }.onFailure { logger.error("State listener of process ${spec.name} failed", it) }
            synchronized(lock) { if (current === this) _state.value = state }
        }
        // watch(), launchProcess(), killLeftovers(), stop() jako dnes, ale jen nad vlastními poli
    }
}
```

- `finally` ve `watch` pracuje jen s `this.process` a `this.instance`, takže novější inkarnaci zabít nemůže.
- `onStateChange` se nesmí volat pod `lock`. Listener smí volat `stop()` nebo `start()` a z jiného vlákna by hrozil
  deadlock.
  - Zápis do `_state` probíhá pod zámkem s kontrolou `current === this`.
  - Pořadí „listener před zápisem stavu“ zůstane zachované.
- `Running` se hlásí uvnitř `start()`, tedy pod zámkem, stejně jako dnes pod zámkem skupiny. Listener pro `Running`
  proto nesmí blokovat (`ProcessGroup` v něm dělá jen `_state.update`).
- `stop()` si ponechá dnešní trik s `gaveUp`: na inkarnaci, která se vzdala, se nečeká (volá se z jejího listeneru).
- Nové testy:
  - `start()` z `onStateChange(GaveUp)`: nový proces žije a stav je `Running`.
  - Dvojí `start()` vyhodí `IllegalStateException`.
  - Souběžný `start`/`stop` na `ManagedProcess` (stejně jako závodní test skupiny).

### 3.3 `RestartBudget`: logika restartů bez procesů

```kotlin
/** Rozhodnutí watchdogu po skončení procesu — bez procesů a času, testovatelné jednotkově. */
internal class RestartBudget(private val policy: RestartPolicy) {
    var restarts: Int = 0
        private set

    /**
     * Proces skončil po [ran] (null = selhalo spuštění, stabilní běh se nepočítá).
     * Vrací pořadí dalšího restartu, null = vyčerpáno → GaveUp.
     */
    fun next(ran: Duration?): Int? {
        if (ran != null && ran >= policy.stableRun) restarts = 0
        if (restarts >= policy.maxRestarts) return null
        return ++restarts
    }

    /** prodleva před restartem — místo pro případný backoff */
    fun delay(): Duration = policy.delay
}
```

Smyčka watchdogu se tím zkrátí na:

```kotlin
while (true) {
    val ran: Duration? = next?.awaitExit()
    val restart: Int = budget.next(ran) ?: return giveUp(budget.restarts)
    if (ran != null) killLeftovers()
    setState(ProcessState.Restarting(restart, exitCode))
    delay(budget.delay())
    next = relaunch(restart)
}
```

Volitelné rozšíření, jen pokud bude poptávka: `RestartPolicy(backoff = 2.0, maxDelay = 1.minutes)`. Dnes padající
proces na kiosku zkusí 50 restartů po 2 s, za zhruba 100 s to vzdá a mezitím zařízení zatěžuje. S backoffem by
vydržel déle a zatěžoval méně.

### 3.4 `ProcessGroups` do jádra, Spring jen jako adaptér

Dnes `ProcessGroups` leží v `autoconfigure` a závisí na `ApplicationEventPublisher`, `InitializingBean` a
`DisposableBean`. `ProcessGroup` má `internal` konstruktor a `ProcessGroupListener` je `internal`. Bez Springu tak
skupinu vůbec nejde vytvořit (testy používají internal konstruktor).

Návrh:
- **Jádro.** `cz.petrbalat.klib.process.ProcessGroups` nabídne:
  - `create`, `find`, `groups`,
  - `reapOrphans(): Int` (dnešní `afterPropertiesSet`),
  - `close()` z `AutoCloseable` (dnešní `destroy()`).

  Konstruktor přijme veřejný `ProcessGroupsListener` s výchozími implementacemi `restarting` a `gaveUp`.
  Odebrání z registru (`stopped`) zůstane interní.
- **Továrna / DSL** `processGroups(owner) { }` (4.4).
- **`autoconfigure`.**
  - Bean `processGroups` se postaví přes DSL s listenerem, který publikuje `ProcessRestartedEvent` a
    `ProcessGaveUpEvent`.
  - `@Bean` má odvozenou destroy metodu `close()`, takže pořadí zůstane jako u `DisposableBean` dnes: zničí se až po
    beanech, které na něm závisí. Zdůvodnění, proč ne `SmartLifecycle`, zůstává v platnosti.
  - Úklid sirotků při startu: `if (properties.reapOrphansOnStartup) reapOrphans()` v bean metodě. Odpovídá dnešnímu
    `afterPropertiesSet`.
  - Beany `ProcessTagging` a `ProcessReaper` zůstanou kvůli přepsatelnosti (`@ConditionalOnMissingBean`) a builder
    je přijme.
- Eventy a `klib.process.*` se nemění. `ProcessAutoConfigurationTest` musí projít beze změny.

---

## 4. Kotlin DSL

### 4.0 Principy

- DSL je tenká vrstva nad neměnnými hodnotami (`ProcessSpec`, `RestartPolicy`, `Segment`). Všechno, co umí DSL, jde
  i bez něj.
- Buildery jen vytvářejí hodnoty. Vedlejší efekt má jen funkce, která to říká názvem: `start`, `withGroup`,
  `runProcess`, `playCycle`, `schedule`.
- Buildery nesou `@DslMarker` (`@ProcessDsl`), takže ve vnořeném bloku (`restart { }` uvnitř `start { }`) nejdou
  implicitně volat metody vnějšího builderu.
  - Označit tak i `ProcessGroup`. Uvnitř `start { }` pak nejde omylem zavolat vnější `start`.
- Validace probíhá v `build()`: `command` musí být zadaný a neprázdný a doby kladné. Chyba tak vznikne na místě
  volání, ne až při forku.
- Výchozí hodnoty skupiny nebo `klib.process.*` dosazuje builder. Explicitní nastavení má přednost.
- DSL nevytváří skryté scopy ani vlákna. Kde to jde, nabízí strukturovanou variantu (`withGroup`, `playCycle`).
- Hierarchie builderů:
  - `CommonProcessBuilder`: command, name, env, output, workingDirectory, gracefulStop, stopStrategy.
  - `ProcessSpecBuilder` (+ restart).
  - `GroupProcessBuilder` (+ `logToFile`, háčky).
  - `SegmentBuilder` (`CommonProcessBuilder` + `prepare`/`whilePlaying`, bez restartu, protože segment je vždy
    `NEVER`).

### 4.1 Příkaz

```kotlin
@ProcessDsl
class CommandBuilder internal constructor(executable: String, args: Array<out String>) {
    fun arg(vararg values: Any)
    fun args(values: Iterable<Any>)

    /** přidá [name], jen když [enabled] */
    fun flag(name: String, enabled: Boolean = true)

    /** null = vynechat; [name] končící `=` → jeden argument `--app=url`, jinak dva `--volume 50` */
    fun option(name: String, value: Any?)
}

fun command(executable: String, vararg args: String, block: CommandBuilder.() -> Unit = {}): List<String>

/**
 * `sudo -n …` — bez hesla selže hned, místo čekání na prompt až do timeoutu.
 * sudo resetuje env → proces nenese tag skupiny ani INSTANCE_ENV; reaper ho nenajde, ukončí ho jen stop přes strom.
 */
fun sudo(command: List<String>): List<String>
```

```kotlin
command("mpv", "--fs") {
    option("--volume=", volume)
    flag("--mute", muted)
    option("--input-ipc-server=", socket?.path)
    arg(file.path)
}
// [mpv, --fs, --volume=50, /media/a.mp4]   (muted = false, socket = null)
```

### 4.2 Popis procesu

```kotlin
@ProcessDsl
open class ProcessSpecBuilder internal constructor(defaults: ProcessDefaults) : CommonProcessBuilder() {
    var restart: RestartPolicy = defaults.restart

    /** úprava kopie aktuální politiky (výchozí ze skupiny / klib.process.restart.*) */
    fun restart(block: RestartPolicyBuilder.() -> Unit)
    fun noRestart()
}

@ProcessDsl
abstract class CommonProcessBuilder internal constructor() {
    var name: String? = null
    var workingDirectory: File? = null
    var output: ProcessOutput = ProcessOutput.Discard
    var gracefulStop: Duration = 5.seconds
    var stopStrategy: StopStrategy = StopStrategy.ParentFirst

    fun command(executable: String, vararg args: String, block: CommandBuilder.() -> Unit = {})
    fun command(command: List<String>)
    fun env(name: String, value: String)
    fun env(values: Map<String, String>)
    fun logTo(file: File, maxBytes: Long = ProcessOutput.ToFile.DEFAULT_MAX_BYTES)
}

@ProcessDsl
class RestartPolicyBuilder internal constructor(base: RestartPolicy) {
    var maxRestarts: Int = base.maxRestarts
    var delay: Duration = base.delay
    var stableRun: Duration = base.stableRun
}

fun processSpec(block: ProcessSpecBuilder.() -> Unit): ProcessSpec
```

Znovupoužitelný popis bez skupiny:

```kotlin
val browser = processSpec {
    name = "chromium"
    command("chromium", "--kiosk", "--noerrdialogs") { option("--app=", url) }
    env("DISPLAY", ":0")
    restart { maxRestarts = 10; stableRun = 10.minutes }
}

ManagedProcess(browser, scope).start()
```

### 4.3 Skupina: `withGroup` a `start { }`

```kotlin
/**
 * Skupina na dobu bloku: vytvoří ji, spustí blok a ve `finally` zastaví (i při zrušení).
 * Watchdogy jsou potomci volající korutiny — strukturovaná souběžnost, žádný úklid přes invokeOnCompletion.
 * Dobíhá-li ještě stop skupiny se stejným id (předchozí běh akce), počká na něj místo chyby „already exists“ (2.6).
 */
suspend fun <T> ProcessGroups.withGroup(id: String, block: suspend ProcessGroup.() -> T): T

/** Spustí proces skupiny; výchozí hodnoty z `klib.process.*`, tag skupiny se přidá do env. */
fun ProcessGroup.start(name: String? = null, block: GroupProcessBuilder.() -> Unit): ManagedProcess

@ProcessDsl
class GroupProcessBuilder internal constructor(/* skupina, defaults */) : ProcessSpecBuilder(defaults) {
    /** `<logs-directory>/<fileName ?: name>.log`; název se vyhodnotí v build() (i z binárky) */
    fun logToFile(fileName: String? = null, maxBytes: Long = ProcessOutput.ToFile.DEFAULT_MAX_BYTES)

    /** synchronně z vlákna watchdogu, před zápisem stavu; smí skupinu zastavit (i přes runBlocking) */
    fun onRestarting(block: (ProcessState.Restarting) -> Unit)
    fun onGaveUp(block: (GiveUp) -> Unit)
}
```

Implementace `withGroup`:

```kotlin
suspend fun <T> ProcessGroups.withGroup(id: String, block: suspend ProcessGroup.() -> T): T {
    awaitStopped(id) // předchozí běh téže akce ještě dobíhá
    return coroutineScope {
        val group = create(id, this)
        try {
            group.block()
        } finally {
            group.stop() // NonCancellable uvnitř; watchdogy skončí → coroutineScope se dokončí
        }
    }
}
```

Uvnitř bloku má `runProcess` s implicitním příjemcem `ProcessGroup` přednost před top-level funkcí (4.5), takže
jednorázové příkazy dostanou tag skupiny automaticky. Je to žádoucí, ale musí to být uvedené v KDoc.

```kotlin
suspend fun play(action: Action) = processGroups.withGroup("action-${action.id}") {
    start("chromium") {
        command("chromium", "--kiosk") { option("--app=", action.url) }
        env("DISPLAY", ":0")
        logToFile()
        restart { maxRestarts = 10 }
        onGaveUp { showErrorScreen(action, it) }
    }
    start {
        command("unclutter", "-idle", "1") // název z binárky
        noRestart()
    }
    awaitCancellation()
}
```

### 4.4 `ProcessGroups` bez Springu

```kotlin
fun processGroups(owner: String, block: ProcessGroupsBuilder.() -> Unit = {}): ProcessGroups

@ProcessDsl
class ProcessGroupsBuilder internal constructor(val owner: String) {
    var envName: String = "KLIB_PROCESS_TAG"
    var logsDirectory: File? = null
    var gracefulStop: Duration = 5.seconds
    var shutdownTimeout: Duration = 8.seconds

    fun restart(block: RestartPolicyBuilder.() -> Unit)
    fun onRestarting(block: (group: ProcessGroup, name: String, restarting: ProcessState.Restarting) -> Unit)
    fun onGaveUp(block: (group: ProcessGroup, giveUp: GiveUp) -> Unit)
}
```

Autokonfigurace pak vypadá takto:

```kotlin
@Bean
@ConditionalOnMissingBean
fun processGroups(properties: ProcessProperties, tagging: ProcessTagging, reaper: ProcessReaper, publisher: ApplicationEventPublisher) =
    processGroups(properties.owner) {
        applyProperties(properties, tagging, reaper)
        onRestarting { group, name, r -> publisher.publishEvent(ProcessRestartedEvent(owner, group.id, name, r.restarts, r.exitCode)) }
        onGaveUp { group, giveUp -> publisher.publishEvent(ProcessGaveUpEvent(owner, group.id, giveUp)) }
    }.also { if (properties.reapOrphansOnStartup) it.reapOrphans() }
// destroy: odvozená metoda close() — stejné pořadí jako dnešní DisposableBean
```

`ProcessGroupTest` pak nepotřebuje internal konstruktor:
`processGroups("test") { logsDirectory = logs; onGaveUp { _, g -> gaveUp += g } }`.

### 4.5 `runProcess`

```kotlin
suspend fun runProcess(executable: String, vararg args: String, block: RunProcessBuilder.() -> Unit = {}): ProcessResult
suspend fun runProcess(command: List<String>, block: RunProcessBuilder.() -> Unit = {}): ProcessResult

/** Jednorázový příkaz s tagem skupiny — stop() skupiny ho dorazí i s potomky. */
suspend fun ProcessGroup.runProcess(executable: String, vararg args: String, block: RunProcessBuilder.() -> Unit = {}): ProcessResult

@ProcessDsl
class RunProcessBuilder internal constructor() {
    var timeout: Duration = 30.seconds
    var workingDirectory: File? = null

    /** strop výstupu ve znacích; drží se konec (chyby bývají na konci), zbytek se dočte a zahodí */
    var maxOutput: Int = 1_000_000
    var charset: Charset = Charsets.UTF_8

    fun args(block: CommandBuilder.() -> Unit)
    fun env(name: String, value: String)
    fun env(values: Map<String, String>)
    fun stdin(text: String)

    /** OS redirect — bez vlákna a roury */
    fun stdin(file: File)
}

class ProcessResult(
    val command: List<String>,
    /** null = proces po timeoutu nešel ukončit (stav D), kód není znám */
    val exitValue: Int?,
    val output: String,
    val timedOut: Boolean,
    val outputTruncated: Boolean,
) {
    val success: Boolean get() = !timedOut && exitValue == 0

    /** [ProcessFailedException] s příkazem, kódem a koncem výstupu; jinak vrací this */
    fun checkSuccess(): ProcessResult
}

class ProcessFailedException(val result: ProcessResult) : RuntimeException(/* command, exit, timedOut, output.takeLast(…) */)
```

```kotlin
val seconds = runProcess("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file.path) {
    timeout = 10.seconds
}.checkSuccess().output.trim().toDouble()

runProcess(sudo(command("/usr/bin/systemctl", "restart", "cdc-display"))) { timeout = 20.seconds }.checkSuccess()

// uvnitř withGroup — tag skupiny automaticky
runProcess("soffice", "--headless", "--convert-to", "pdf", doc.path) {
    workingDirectory = tmp
    timeout = 2.minutes
}
```

Uvnitř implementace se vyřeší body 2, A, 2.2 a 2.3:
- `terminate()` = strom s `Duration.ZERO` + `killByEnv`. Volá se při timeoutu i v `catch` (s `addSuppressed`).
- Exit kód po timeoutu: `withTimeoutOrNull(KILL_WAIT) { onExit().await() }`, jinak `null`.
- `stdin == null`: synchronní `outputStream.close()`, žádný job.
- Čtení do kruhového bufferu o velikosti `maxOutput`.

### 4.6 Segmenty a cyklus

```kotlin
fun segment(duration: Duration, block: SegmentBuilder.() -> Unit): Segment

@ProcessDsl
class SegmentBuilder internal constructor() : CommonProcessBuilder() {
    /** příprava před startem (touch souborů, úklid lock souborů); smí blokovat — zrušení cyklu na ni nečeká */
    fun prepare(block: () -> Unit)

    /** běží paralelně po dobu segmentu, na konci se zruší */
    fun whilePlaying(block: suspend () -> Unit)
}

/** Neměnný popis — runtime stav je v [SegmentCyclePlayer.nowPlaying]. */
class Segment internal constructor(
    val duration: Duration,
    val spec: ProcessSpec, // restart vždy NEVER
    val prepare: () -> Unit,
    val whilePlaying: (suspend () -> Unit)?,
)

class NowPlaying(val segment: Segment, val process: ManagedProcess, val startedAt: TimeMark)

class SegmentCyclePlayer(
    scope: CoroutineScope,
    minSegment: Duration = 5.seconds,
    /** přidá se ke každému segmentu (tag skupiny) */
    environment: Map<String, String> = emptyMap(),
) {
    val nowPlaying: StateFlow<NowPlaying?>

    /** běžící cyklus → IllegalStateException */
    fun start(name: String, emptyRetry: Duration = 30.seconds, segments: suspend () -> List<Segment>)

    /** dnešní stopAndJoin — sjednocení s ManagedProcess.stop / ProcessGroup.stop */
    suspend fun stop()
}

/**
 * Strukturovaná varianta: cyklus běží v aktuální korutině, dokud ji nikdo nezruší nebo se nezastaví skupina.
 * Segmenty nesou tag skupiny. Cyklus se ve skupině zaregistruje a stop() skupiny ho nejdřív zruší a počká na něj (2.5).
 */
suspend fun ProcessGroup.playCycle(
    name: String,
    minSegment: Duration = 5.seconds,
    emptyRetry: Duration = 30.seconds,
    segments: suspend () -> List<Segment>,
)
```

```kotlin
processGroups.withGroup("action-${action.id}") {
    start("chromium") { /* … */ }
    playCycle("slides-${action.id}") {
        playlist.load(action).map { item ->
            segment(item.duration) {
                command("mpv", "--fs", "--no-osc", item.file.path)
                prepare { removeStaleLocks(item) }
                whilePlaying { paginator.run(item) }
            }
        }
    }
}
```

`ProcessGroup` k tomu potřebuje interní evidenci připojených jobů (`attach(job)`). `stop()` pak postupuje takto:
1. Zruší připojené cykly a počká na ně. Cykly přitom zastaví své segmenty.
2. Zastaví procesy jako dnes. Při čekání dál přeskočí watchdog, který se vzdal a právě volá listener.
3. Zavolá reaper.

### 4.7 `IntervalScheduler`

```kotlin
fun schedule(id: K, start: LocalDateTime, end: LocalDateTime?, block: IntervalBuilder<K>.() -> Unit)

@ProcessDsl
class IntervalBuilder<K : Any> internal constructor() {
    /** po dosažení start, před onStart; zrušitelné — nestihne-li se do end, interval se přeskočí */
    fun awaitReady(block: suspend () -> Unit)

    /** povinné — build() bez něj vyhodí */
    fun onStart(block: suspend (IntervalHandle) -> Unit)
    fun onEnd(block: suspend (EndReason) -> Unit)
}

enum class EndReason {
    /** dosažen `end` */
    Ended,
    /** cancel(id) / IntervalHandle.cancelAndJoin(), i nový plán téhož id, který se přeskočil (2.7) */
    Cancelled,
    /** nahrazen novým plánem téhož id — akce se hned restartuje */
    Replaced,
    /** shutdown() scheduleru — plán po startu aplikace pokračuje, neoznačovat jako dokončený */
    Shutdown,
    /** onStart vyhodil nebo překročil callbackTimeout — úklid případně rozběhnuté akce (2.8) */
    StartFailed,
}
```

Proč změnit dnešní signaturu:
- `schedule(id, start, end, onStart, onEnd, awaitReady)` má tři funkční parametry a trailing lambda se váže na
  **poslední** z nich, tedy `awaitReady`. Zápis `schedule(id, s, e, onStart = { … }) { download() }` vypadá jako
  tělo startu, ale je to `awaitReady`. Dnes je nutné psát pojmenované argumenty, DSL tuhle past odstraní.
- `onEnd(replaced: Boolean)` nerozliší přirozený konec, zrušení a shutdown. Konzument, který plán po `onEnd` označí
  za dokončený, ho dnes označí i při vypnutí aplikace. Po restartu pak plán nepokračuje, i když měl.

```kotlin
scheduler.schedule(action.id, action.start, action.end) {
    awaitReady { downloads.await(action) }
    onStart { handle ->
        conflicts.preempt(action, handle)
        runner.start(action)
    }
    onEnd { reason ->
        runner.stop(action)
        if (reason == EndReason.Ended || reason == EndReason.Cancelled) plans.markFinished(action.id)
    }
}
```

`callbackTimeout` sjednotit na `kotlin.time.Duration`. Interní výpočty převést na `Instant` s čekáním po krocích (2.4).

---

## 5. Přehled změn rozhraní

| Dnes | Návrh |
|---|---|
| `ManagedProcess(command, name, output, restart, gracefulStop, environment, scope, onStateChange)` | `ManagedProcess(spec, scope, onStateChange)` + `processSpec { }`; nově `restart()`; dvojí `start()` vyhodí `IllegalStateException` |
| `ManagedProcess.command / name / output` | `ManagedProcess.spec` |
| `group.start(command, output, restart, name, environment)` | `group.start(spec)` / `group.start("mpv") { … }` |
| `processGroups.create(id, scope)` + ruční `try/finally { stop() }` | `processGroups.withGroup(id) { … }` (`create` zůstává pro dlouho žijící skupiny) |
| `ProcessGroups` jen jako Spring bean v `autoconfigure` | `ProcessGroups : AutoCloseable` v jádru + `processGroups(owner) { }`; Spring jen publikuje eventy |
| `ProcessGroupListener` internal | veřejný `ProcessGroupsListener` / DSL `onRestarting`, `onGaveUp` (skupina i proces) |
| `GiveUp(name, command, restarts, output)` | `GiveUp(spec, restarts)` |
| `runProcess(command, timeout, stdin, environment)` | `runProcess(command) { timeout; stdin(); env(); workingDirectory; maxOutput }`, `group.runProcess(…)` |
| `ProcessResult(exitValue: Int, output, timedOut)` | `exitValue: Int?`, `command`, `outputTruncated`, `checkSuccess()` |
| `Segment(duration, command, environment, output, prepare, whilePlaying)` + `segment.process / isAlive` | `segment(duration) { command(…); prepare {}; whilePlaying {} }`, `player.nowPlaying` |
| `SegmentCyclePlayer.stopAndJoin()` | `stop()`; nově `group.playCycle { }` |
| `IntervalScheduler.schedule(id, start, end, onStart, onEnd(replaced), awaitReady)` | `schedule(id, start, end) { awaitReady {}; onStart {}; onEnd { reason -> } }` |
| `IntervalScheduler(clock, callbackTimeout: kotlin.time.Duration)` + interně `java.time.Duration` | jen `kotlin.time.Duration`; interně `Instant` |
| `rotateLog` v `processStop.kt` | `processOutput.kt` |
| bez pracovního adresáře | `workingDirectory` ve spec i v `runProcess` |

Na `master` modul ještě není, takže starou podobu není nutné držet ani jako `@Deprecated`.

---

## 6. Doporučený postup

Každý krok je samostatný commit se zelenými `:spring-boot-process:test` a `:spring-boot-process:dockerTest`.

1. **Opravy bez změny API** (každá s testem, který dnes selže):
   - 2.1: inkarnace v `ManagedProcess`. Testy: `start()` z `onStateChange(GaveUp)`, dvojí `start()`, souběžný
     `start`/`stop`.
   - Body 2, A, 2.2 a 2.3 v `runProcess`. Testy:
     - `maxOutput`,
     - démon po double-forku zabitý i při timeoutu.

     Stav D se v testu simuluje špatně, ten ověřit review a ručně (`fuse` / `nfs` v kontejneru je zbytečně
     drahé).
   - 2.4 a 2.7 v `IntervalScheduler`. Testy s měnitelným `Clock`:
     - skok hodin dopředu i dozadu,
     - nahrazení plánem s `end` v minulosti, po kterém přijde `onEnd(replaced = false)`.
   - 2.11 (`check` v `SegmentCyclePlayer.start`) a pořadí `processes +=` v `ProcessGroup.start` (2.9).
2. **`RestartBudget`** (3.3) s jednotkovými testy bez procesů. `ManagedProcessTest` zůstane jako integrační.
3. **`ProcessSpec`** (3.1):
   - `ManagedProcess(spec)`, `ProcessGroup.start(spec)`, `GiveUp(spec)`, `Segment(spec)`,
   - `workingDirectory`,
   - přesun `rotateLog`,
   - interní launcher `LaunchedProcess(process, signalTarget)` jako příprava na kap. 7.4.
4. **`ProcessGroups` do jádra** a `processGroups {}` + Spring adaptér (3.4). `ProcessAutoConfigurationTest` musí projít
   beze změny.
5. **DSL** (kap. 4):
   - `command` / `sudo`, `processSpec`,
   - `group.start {}`, `withGroup` (včetně čekání na dobíhající skupinu, 2.6),
   - `runProcess {}`, `segment {}` + `playCycle` (2.5),
   - `schedule {}` + `EndReason` (2.8).
6. **Volitelné:**
   - rotace logu za běhu (2.10),
   - `StopStrategy.WholeTree` (2.12),
   - backoff restartů (3.3),
   - tag `name` u Micrometer počítadel (nízká kardinalita, na rozdíl od `groupId`),
   - `ProcessOutput.Inherit` (OS redirect do výstupu JVM pro vývoj),
   - `ProcessIsolation.PidNamespace` (kap. 7), ale až po ověření na zařízení (7.5).
7. **Dokumentace:**
   - `README.md` (příklady v DSL, `sudo` a env),
   - `spring-boot-process/CLAUDE.md` (tok zastavení s cykly, inkarnace, vlákno eventů),
   - `.claude/project-info.md`.

### Co záměrně neměnit

- `close()` / `DisposableBean` místo `SmartLifecycle`. Zdůvodnění v KDoc `ProcessGroups` platí dál.
- Synchronní háček volaný **před** zápisem do `StateFlow`. Flow místo háčku by porušilo záruku „listener smí skupinu
  synchronně zastavit“ i pořadí stavů skupiny a procesu.
- Testy v reálném čase bez `runTest`. Nová čistá logika (`RestartBudget`, výpočet času v `IntervalScheduler` přes
  `Clock`) jde testovat i bez čekání.
- Výstup `ManagedProcess` přes OS redirect, nikdy přes rouru do JVM.
- `CoroutineStart.UNDISPATCHED` u watchdogu. Jen doplnit KDoc (2.13).

---

## 7. Izolace přes PID namespace (`unshare`): vyhodnocení

Návrh: spouštět procesy v samostatném PID namespace přes `unshare` z util-linux (v Debianu je). Když skončí init
namespace, jádro pošle SIGKILL všem procesům v namespace, včetně odpojených (`setsid`, double-fork). Teoreticky to
nahrazuje tag v env, sken `/proc` i `INSTANCE_ENV`. Z podkladu jsem viděl jen první ze tří zmíněných řešení.

### 7.1 Ověřeno v kontejneru

Pokusy běžely v image `klib-process-test` (Debian trixie, util-linux 2.41.5, tini, uid 1000, `sudo` jen na
`/usr/bin/sleep`, tedy podmínky produkčního kontejneru) na Docker Desktop 29.8. **Docker Desktop nemá AppArmor**, na
Debian hostu je proto potřeba pokusy zopakovat (7.5). Pokud není uvedeno jinak, kontejner běžel s
`--cap-add SYS_ADMIN`.

| # | Pokus | Výsledek |
|---|---|---|
| 1 | `unshare --pid --fork` jako uid 1000 | **selže** (EPERM). `CapEff` je 0, procesy mimo root capability kontejneru nemají |
| 2 | `unshare --user --map-current-user --pid --fork --kill-child` | **OK**. Uvnitř `pid=1`, `uid=1000` |
| 2' | totéž **bez** `--cap-add SYS_ADMIN` | **selže** (EPERM). Výchozí seccomp profil Dockeru bez SYS_ADMIN blokuje `unshare(CLONE_NEWUSER)` |
| 3 | `--mount-proc` | **selže** (EPERM). Docker maskuje cesty v `/proc` a jádro nový proc mount v userns odmítne |
| 4 | `/proc` bez `--mount-proc` | **pozor**: `getpid()` = 1, ale `/proc/1` je tini kontejneru. `/proc/self` funguje |
| 5 | `sudo` uvnitř namespace | **selže**: „/usr/bin/sudo must be owned by uid 0 and have the setuid bit set“ |
| 6 | SIGTERM na proces `unshare` (to dělá `Process.destroy()`) | **pozor**: `unshare` SIGTERM zablokuje a dítěti ho nepřepošle. Graceful stop tak nenastane, vždy se čeká až na SIGKILL |
| 7 | SIGKILL na `unshare` | **OK**. Zanikne celý namespace včetně vnuka spuštěného přes `setsid` |
| 8 | Pád wrapperu (init namespace skončí s kódem 3) | **OK**. Pozůstatky včetně `setsid` zabije jádro, exit kód 3 projde přes `unshare` |
| 9 | SIGTERM na PID 1 namespace bez handleru (`sleep`) | **pozor**: signál se ignoruje (sémantika initu) |
| 9b | `tini` jako init namespace + SIGTERM | **OK**. Signál přepošle a skončí |
| 10 | Init namespace, který nereapuje | **pozor**: vznikají zombie, init uvnitř proto musí být `tini` |

`--cap-add SYS_ADMIN` se tu tedy neuplatní jako capability procesu (`CapEff` = 0). Jen odemyká syscall v seccomp
profilu. Funguje proto jen varianta s user namespace.

### 7.2 Co by to přineslo

- Úklid celého stromu by garantovalo jádro, a to i pro `setsid` a double-fork a i pro procesy, které si vyčistí env
  (`env -i`, některé launchery). Odpadne sken `/proc` a riziko recyklace PID.
- Pád wrapperu okamžitě ukončí všechny jeho potomky (test 8). `INSTANCE_ENV` a `killLeftovers` jsou pro izolované
  procesy zbytečné.
- V `runProcess` zanikne namespace hned po skončení příkazu. Potomek, který drží rouru, zemře okamžitě a odpadne
  čekání `OUTPUT_GRACE`. Změní se tím ale sémantika: záměrně spuštěný démon (`cmd &`) už nepřežije.

### 7.3 Co to stojí a co to nevyřeší

- **Vyžaduje `--cap-add SYS_ADMIN`** (seccomp) a userns povolené na hostu. Knihovna to nesmí předpokládat, potřebuje
  detekci a fallback.
- **`sudo` uvnitř nefunguje** (test 5). Procesy pod `sudo` izolovat nejde, a jsou to právě ty, které reaper nevidí.
  Omezení z README tak zůstává.
- **Posunutý `/proc`** (test 4). Program, který čte `/proc/<getpid()>`, čte cizí proces. Chromium, mpv a LibreOffice
  je potřeba ověřit na zařízení. `--mount-proc` by vyžadoval `--security-opt systempaths=unconfined`, což oslabí
  kontejner.
- **Graceful stop vyžaduje změnu.** `Process` drží `unshare`, ale SIGTERM je nutné poslat initu namespace (dítěti
  `unshare`) a ten musí být `tini` (testy 6, 9, 9b). `stopGracefully` proto potřebuje jiný cíl signálu, než je handle
  procesu.
- `ProcessState.Running.pid` je PID procesu `unshare`, ne samotného programu.
- **Po pádu nebo restartu JVM** přejde `unshare` pod init kontejneru a běží dál. Úklid podle tagu při startu tedy
  zůstává. Stačí ale zabít otagovaný `unshare`, namespace zanikne s ním.
- **Na macOS (vývoj) a v kontejnerech bez SYS_ADMIN** nic z toho nefunguje. Současný mechanismus (strom, tag,
  `INSTANCE_ENV`) musí zůstat celý.
- **Nepoužívat `PR_SET_PDEATHSIG` vůči JVM** (`setpriv --pdeathsig`). Signál se váže na **vlákno**, které proces
  spustilo. Po zániku nečinného vlákna `Dispatchers.IO` by zabil zdravý proces. `--kill-child` v pořádku je, protože
  se váže na jednovláknový `unshare`.
- **Chromium sandbox** si vytváří vlastní user namespace. Vnořený userns by měl projít, ale je potřeba to ověřit.
- **Alternativy (neověřeno):**
  - cgroup v2 (`cgroup.kill`) dává stejnou záruku a k tomu limity paměti pro Chromium. V Dockeru je ale cgroupfs
    standardně jen pro čtení, takže by šlo o větší zásah do kontejneru.
  - `PR_SET_CHILD_SUBREAPER` na JVM nepoužívat. JVM nereapuje procesy, které sama nespustila, a vznikaly by zombie.

### 7.4 Doporučení

Nedělat před refaktoringem, ale ani to nezahodit:

1. **Nenahrazuje žádnou opravu z kap. 2.** Sdílený stav v `ManagedProcess`, `waitFor` a výstup v `runProcess`,
   `IntervalScheduler`, vztah cyklu a skupiny i `create` je potřeba opravit tak jako tak.
2. **Nenahradí tagy.** Ty zůstávají potřeba jako fallback pro macOS, `sudo`, kontejnery bez SYS_ADMIN a sirotky po
   pádu JVM. Izolace by byla přídavná vrstva na Linuxu.
3. **Bez `ProcessSpec` by se musela protáhnout čtyřmi API** (`ManagedProcess`, `ProcessGroup.start`, `Segment`,
   `runProcess`), tedy přesně tou duplicitou, kterou refaktoring odstraňuje. Po refaktoringu je to lokální změna.
4. **Rozhodující neznámé se ověří jen na zařízení**: AppArmor na Debian hostu a Chromium, mpv a LibreOffice
   s posunutým `/proc`. To může běžet paralelně s refaktoringem (7.5).

Do plánu promítnout už teď (krok 3 v kap. 6):
- Jeden interní launcher, tedy jediné místo, kde ze `ProcessSpec` vzniká `ProcessBuilder` (env, tag,
  `INSTANCE_ENV`, redirect, `workingDirectory`).
- Launcher vrací `LaunchedProcess(process, signalTarget)` a `stopGracefully` posílá SIGTERM na `signalTarget`. Dnes
  jsou obě hodnoty stejné, s namespace by `signalTarget` byl init namespace.

Návrh API (krok 6, volitelný):

```kotlin
sealed interface ProcessIsolation {
    /** strom procesu + tag v env + /proc — dnešní chování, i macOS */
    data object None : ProcessIsolation

    /**
     * Linux: vlastní PID namespace — `unshare --user --map-current-user --pid --fork --kill-child -- tini -- <command>`.
     * Konec initu namespace (pád programu, stop) ukončí jádro celý namespace, i odpojené potomky.
     * Ne pro sudo; vyžaduje unshare + tini a kontejner s `--cap-add SYS_ADMIN`, jinak [None] (s varováním v logu).
     */
    data object PidNamespace : ProcessIsolation
}
```

- `ProcessSpec.isolation` s výchozí hodnotou z `klib.process.isolation=none|auto|pid-namespace`.
  - Výchozí hodnota je `none`, dokud se izolace neověří na zařízení.
  - `auto` jednou při startu `ProcessGroups` zkusí `unshare … true` a podle výsledku izoluje.
  - Příkaz, který začíná `sudo`, se neizoluje nikdy.
- Zastavení: SIGTERM na dítě `unshare` (tini ho přepošle programu). Po `gracefulStop` SIGKILL na `unshare` a
  `--kill-child` ukončí namespace.
- `runProcess` má výchozí `None` kvůli `sudo` a kvůli sémantice démonů (7.2).

### 7.5 Ověření na zařízení (produkční kontejner, uid aplikace)

```sh
# úklid odpojeného potomka po pádu wrapperu
unshare --user --map-current-user --pid --fork --kill-child -- tini -- sh -c 'setsid sleep 999 & exit 3'; echo "exit=$?"
pgrep -f 'sleep 999' || echo "úklid OK"

# skutečné programy s posunutým /proc a vnořeným userns (Chromium sandbox)
unshare --user --map-current-user --pid --fork --kill-child -- tini -- chromium --headless --dump-dom about:blank
unshare --user --map-current-user --pid --fork --kill-child -- tini -- soffice --headless --convert-to pdf test.odt
unshare --user --map-current-user --pid --fork --kill-child -- tini -- mpv --frames=50 --vo=null test.mp4
```

Když některý z příkazů selže, izolace se nezavádí a refaktoring proběhne beze změny.

## 8. Izolace přes process group (`setsid`): vyhodnocení

Návrh: spouštět každý proces přes `setsid` (util-linux, v Debianu je i ve `slim`). Proces dostane vlastní session
a process group s pgid = jeho PID. Zastavení by pak bylo jedno `kill(-pgid, signál)` místo stromu
z `ProcessHandle.descendants()` a hledání pozůstatků v `/proc` podle env. Cíl: jednodušší zabíjení.

### 8.1 Dnešní mechanismy

| Mechanismus | Kde | Co řeší |
|---|---|---|
| strom: snímek `descendants()`, SIGTERM rodiči, SIGKILL potomkům | `stopGracefully` (`ManagedProcess` stop, `runProcess` timeout/zrušení) | běžný stop |
| `INSTANCE_ENV` + sken `/proc` | `Run.killLeftovers` (restart po pádu, stop, start po GaveUp), `ProcessGroup.start` (nahrazení procesu), `runProcess` (timeout, zrušení, potomek držící rouru) | potomci, kteří přežili rodiče, i ti odpojení přes `setsid` |
| tag skupiny + sken `/proc` | `ProcessGroup.stop` → `ProcessReaper.kill` | odpojení sirotci skupiny |
| tag vlastníka + sken `/proc` | `ProcessGroups.reapOrphans` při startu aplikace | sirotci z minulého běhu JVM |

### 8.2 Ověřeno v kontejneru

Image `klib-process-test` (Debian trixie, tini, uid 1000, `sudo` jen na `/usr/bin/sleep`), **bez** `--cap-add`.
Procesy spouštěla JVM přes `ProcessBuilder`, signál skupině posílalo FFM volání `kill()` z libc.

| # | Pokus | Výsledek |
|---|---|---|
| 1 | potomek JVM bez `setsid` | sdílí process group JVM (pgid = PID JVM). Signál „skupině procesu“ by dnes zabil i JVM, `setsid` je proto podmínka |
| 2 | `setsid` jako prefix příkazu | **OK**. Potomek JVM není vedoucí skupiny, takže `setsid` neforkuje a jen `exec`ne: `Process.pid()` je cílový program, pid = pgid = sid. Bez capability |
| 3 | pád wrapperu (`sh -c 'sleep 1000 & exit 1'`) | **OK**. Přeživší potomek (ppid=1) zůstává ve skupině, `kill(-pgid, SIGKILL)` ho zabije. Pak `kill(-pgid, 0)` = ESRCH |
| 4 | potomek se sám odpojí přes `setsid` (`detacher.sh`, démoni) | **unikne**. Má vlastní session i pgid, `kill(-pgid)` ho mine. Najde ho jen tag v env |
| 5 | wrapper `sh -c 'program; …'` + SIGTERM | `Process.destroy()` (dnes): program TERM **nedostal**, wrapper skončil a program běžel dál (dnes ho pak zabije SIGKILL ze snímku potomků). `kill(-pgid, SIGTERM)`: program TERM dostal a skončil sám |
| 6 | `setsid sudo -n /usr/bin/sleep 1000` | sudo i root `sleep` jsou ve stejné skupině. `kill(-pgid, TERM)` vrátí OK (sudo signál dostane, root proces ne), sudo ho přepošle a skončí oba. `kill(-pgid, KILL)` zabije jen sudo, root `sleep` běží dál pod initem — stejně jako dnes |
| 7 | signál skupině z JVM | Java API nemá (`ProcessHandle.destroy()` jen na PID). FFM `kill()` funguje, ale bez `--enable-native-access=ALL-UNNAMED` vypíše JVM 4 řádky `WARNING: A restricted method … Restricted methods will be blocked in a future release`. Spuštění `kill -s TERM -- -<pgid>` (procps) trvá ~1 ms |
| 8 | prázdná skupina | po skončení všech členů `kill(-pgid, 0)` = ESRCH, číslo se uvolní pro nové PID. `pid_max` v kontejneru 4 194 304 |
| 9 | macOS (host) | binárka `setsid` neexistuje |

### 8.3 Co by to přineslo

- **SIGTERM celé skupině naráz** (pokus 5). Program za wrapperem (`sh -c "mpv …"`, spouštěcí skripty) dostane šanci
  uklidit, třeba lock soubory profilu. To je řešení 2.12, lepší než `StopStrategy.WholeTree`: nepotřebuje snímek
  potomků.
- **Atomičnost vůči forku.** `kill(-pgid)` zasáhne i potomky vzniklé po snímku `descendants()` (helpery, které
  Chromium spouští i během zastavování). Dnes je snímek před `destroy()` a pozdější potomky chytí až sken podle env.
- **Pozůstatky po pádu bez skenu `/proc`** (pokus 3), pokud se neodpojily přes `setsid`.
- **`runProcess`**: timeout, zrušení i potomek držící rouru (`cmd &`, double-fork bez `setsid`) jedním
  `kill(-pgid, SIGKILL)`.
- Na rozdíl od PID namespace (kap. 7) bez capability a bez změny kontejneru, `sudo` funguje (pokus 6) a
  `ProcessState.Running.pid` zůstává PID programu (pokus 2).

### 8.4 Co to nevyřeší a co to stojí

- **Procesy odpojené přes `setsid` unikají** (pokus 4). Kvůli nim existuje tag (`ProcessReaper`: „dědí ho i odpojení
  potomci (`setsid`, `soffice.bin`, `ssh` pod autossh)“). `INSTANCE_ENV` je dnes chytí i **před restartem**, což je
  u LibreOffice scénář „zámek profilu“. Kdyby `killLeftovers` přešel jen na pgid, odpojený `soffice.bin` by pád
  wrapperu přežil a nová instance by narazila na zámek.
- **Sirotci po pádu JVM** (`reapOrphans`): pgid z minulého běhu nikdo nezná. Šlo by ho ukládat do souboru, ale
  i pak hrozí recyklace čísla (viz níže). Sken podle tagu vlastníka zůstává.
- **Signál skupině z JVM nemá API** (pokus 7):
  - FFM: varování v logu každé aplikace a v budoucnu zablokování. Konzument by musel přidat
    `--enable-native-access=ALL-UNNAMED`, u Spring Boot fat jaru `Enable-Native-Access: ALL-UNNAMED` v manifestu.
  - Spouštění `kill`: proces navíc pro každý signál a závislost na procps (ve `slim` image není). Jde to i přes
    builtin `bash -c 'kill -s TERM -- "-$1"'`. Builtin `dash` (`/bin/sh` v Debianu) signál skupině poslat neumí
    (`Illegal option -8`, `--` nezná).
- **Čekání na prázdnou skupinu nemá API** (pro skupinu neexistuje pidfd). Zbývá polling `kill(-pgid, 0)`, nebo čekat
  jen na vedoucí proces (`onExit`) a pak poslat SIGKILL skupině naslepo.
- **Recyklace čísla pgid.** Dokud má skupina člena, číslo se nepřidělí. Jakmile je prázdná, smí ho jádro dát novému
  procesu. Pozdní `kill(-pgid)` by pak zabil cizí skupinu stejného uživatele. Týká se to dnešního `killLeftovers` po
  GaveUp, který běží až při dalším `start()` nebo `stop()`, klidně za hodiny. UUID v env tímhle netrpí. Na 64bit
  Debianu je `pid_max` 4 194 304, na 32bit ARM (RPi OS 32bit) jen 32 768, tam je riziko reálné. Bezpečný je jen
  signál hned po konci vedoucího procesu, jinak je nutné ověřit členství, a to zase přes `/proc`.
- **macOS** (pokus 9): dnešní strom musí zůstat jako fallback, v kódu tak budou dvě cesty místo jedné.
- **Změny chování:**
  - SIGTERM dostanou všichni členové naráz. Chromium si renderery ukončuje samo (proto byla 2.12 volitelná).
    U kiosku je souběžný TERM rendererům neškodný, ale je to změna.
  - Nová session nemá řídicí terminál. Ctrl+C při vývoji (`bootRun`) už procesy nezasáhne přímo, zastaví je až
    shutdown hook JVM. Po `kill -9` JVM přežijí do příštího `reapOrphans` (dnes je Ctrl+C zabije s JVM). V Dockeru
    a pod systemd (`KillMode=control-group`) beze změny.

### 8.5 Bilance pro zjednodušení

| | Odpadne (Linux) | Přibude |
|---|---|---|
| launcher | — | prefix `setsid` + detekce (`/usr/bin/setsid`), jinak dnešní cesta |
| stop procesu | snímek `descendants()` | `signalGroup(pgid, sig)` přes FFM nebo `kill`, čekání na vedoucí + SIGKILL skupině |
| pozůstatky | `killByEnv(INSTANCE_ENV)` jen pokud se smíříme s únikem `setsid` potomků mezi restarty | ochrana proti recyklaci pgid u pozdního úklidu po GaveUp |
| `runProcess` | `killByEnv(INSTANCE_ENV)` (2×) | `signalGroup` |
| macOS | — | stromová cesta zůstává celá |

Beze změny zůstává `ProcessReaper`, `ProcessTagging`, tag skupiny, `reapOrphans` a `findProcessesByEnv`. **Sken
`/proc` nezmizí** a kód se nezjednoduší: mechanismy se přidají, nenahradí. Process group je lepší ve dvou věcech,
které nejsou zjednodušením: graceful stop celé skupiny (2.12) a atomický kill vůči forku.

### 8.6 Doporučení

1. **Sken `/proc` podle env nenahrazovat.** Ani process group, ani PID namespace (kap. 7) nepokryjí v neprivilegovaném
   kontejneru procesy odpojené přes `setsid` a sirotky po pádu JVM. Tag v env je jediný mechanismus, který chytí
   všechno, co env zdědí.
2. **Process group zavést jen jako řešení 2.12, ne jako zjednodušení** (volitelné, kap. 6 bod 6). **Po měření
   v 8.8 nedělat** — skutečným programům by to nepomohlo (8.9).
   - Launcher na Linuxu přidá prefix `setsid`, na macOS beze změny.
   - Stop: `kill(-pgid, TERM)` → čekat na vedoucí proces nejvýš `gracefulStop` → **hned** `kill(-pgid, KILL)`.
     Číslo je v tu chvíli ještě obsazené zbylými členy, nebo právě uvolněné; recyklace by vyžadovala oběh celého
     prostoru PID během mikrosekund.
   - Pak `killLeftovers` podle `INSTANCE_ENV` jako dnes (odpojení přes `setsid`). Pozdní úklid po GaveUp jen přes env,
     nikdy přes pgid.
   - Signál přes spuštění `kill` (stop je vzácný, ~1 ms nevadí), ne FFM. Knihovna nesmí aplikacím přidat varování
     ani vyžadovat přepínač JVM.
3. **Jestli je cílem jednodušší kód**, dává větší smysl sjednotit dnešní úklid. `runProcess.terminate()` a
   `Run` dělají totéž (`stopGracefully` + `killByEnv(INSTANCE_ENV)`), jen s jiným `gracefulStop`. Jedna interní
   funkce `terminate(process, instance, gracefulStop)` ve `processStop.kt` by byla jediné místo, kde se zabíjí jedno
   spuštění. Process group by se pak případně přidala jen tam.
   **Hotovo** (`refactor(process): jedno místo pro spuštění a zabití procesu (Launched)`): `launch()` v `Launched.kt`
   vytvoří proces s novým `INSTANCE_ENV`, `Launched.terminate()` = strom + pozůstatky podle env,
   `Launched.killLeftovers()` jen pozůstatky.

### 8.7 Ověření na zařízení

Rozhodující neznámá je, které skutečné programy se ze skupiny odpojí. Na zařízení (uid aplikace) spustit program přes
`setsid` a porovnat SID jeho procesů: stejné SID = drží se ve skupině, jiné SID = odpojil se přes `setsid`.

```sh
setsid libreoffice --headless --norestore --accept='socket,host=localhost,port=2002;urp;' & sleep 5
ps -o pid,ppid,pgid,sid,comm -u "$(id -u)" --sort=sid | grep -E 'SID|soffice|oosplash'

setsid chromium --kiosk about:blank & sleep 5
ps -o pid,ppid,pgid,sid,comm -u "$(id -u)" --sort=sid | grep -E 'SID|chrom|crashpad'
```

Pokud se odpojují `soffice.bin` nebo helpery Chromia (crashpad), platí 8.4 beze zbytku a tag v env je nutný i pro
úklid před restartem.

**Naměřeno (2026-10-10, RPi4, kontejner `cdc-client`, LibreOffice, čistý start přes `setsid`):**

| proces | pid | ppid | pgid | sid |
|---|---|---|---|---|
| `oosplash` | 620 | 1 | 620 | 620 |
| `soffice.bin` | 667 | 620 | 620 | 620 |

`soffice.bin` se ze skupiny **neodpojil**: má stejné pgid i sid jako `oosplash`, který vede skupinu (ppid=1 je jen
důsledek forku v `setsid`). Server poslouchal na `127.0.0.1:2002`. Pro `soffice` by tedy `kill(-pgid, SIGKILL)`
zasáhl celý strom a odpojení přes `setsid` zde nehrozí.

Neověřeno: chování po pádu nebo restartu `soffice.bin` (obnova po pádu) a Chromium (`crashpad`, helpery), které je
potřeba změřit tam, kde ho aplikace skutečně používá.

### 8.8 Měření v kontejneru: Chromium a LibreOffice

`debian:trixie` + `chromium` 154.0.8037.92 a `libreoffice-core-nogui` 25.2.3, tini, uid 1000. Programy spouštěl
neinteraktivní `sh` přes `setsid` (jako JVM s prefixem: `setsid` neforkuje, pid = pgid = sid). Chromium headless
s `--no-sandbox` (kontejner bez userns), LibreOffice `--headless --accept`. Signál skupině přes `/usr/bin/kill`.

| # | Pokus | Výsledek |
|---|---|---|
| 1 | struktura LibreOffice | `oosplash` (vedoucí) → `soffice.bin`, stejné pgid i sid — potvrzuje měření z RPi. Oba nesou env |
| 2 | SIGTERM jen `oosplash` (dnešní `Process.destroy()`) | do 400 ms skončí `oosplash` i `soffice.bin` |
| 3 | SIGTERM skupině / jen `soffice.bin` | stejné jako 2 |
| 4 | profil `~/.config/libreoffice/4/.lock` | po SIGTERM **zůstane ve všech třech variantách** — LibreOffice ho na SIGTERM nemaže |
| 5 | pád `soffice.bin` (SIGKILL) | `oosplash` skončí také a nic nezůstane; watchdog normálně restartuje |
| 6 | pád `oosplash` (SIGKILL) | `soffice.bin` **přežije** (ppid=1, stejné pgid, nese env). `kill(-pgid, KILL)` ho zabije |
| 7 | struktura Chromia | hlavní proces, zygote a renderery ve skupině. **2× `chrome_crashpad_handler` se odpojí** (double fork + `setsid`: ppid=1, vlastní pgid i sid), env ale zdědí |
| 8 | SIGTERM jen hlavnímu procesu | do 4 s skončí vše včetně crashpad |
| 9 | pád hlavního procesu (SIGKILL) | do 3 s skončí vše včetně rendererů a crashpad, nic s tagem nezůstane |

### 8.9 Co z měření plyne

- **Oprava 8.4:** `soffice.bin` se neodpojuje (RPi i kontejner), odpojuje se `chrome_crashpad_handler`. Komentáře
  v `ProcessReaper` a fixtures `detacher.sh` / `orphan-on-crash.sh` opraveny podle měření.
- **Žádný mechanismus úklidu odstranit nejde**, každý má změřený nebo doložený případ:

  | Mechanismus | Případ |
  |---|---|
  | `INSTANCE_ENV` (pozůstatky před restartem a při stop) | pád `oosplash` → `soffice.bin` přežije (6) a drží profil i socket `--accept`; nová instance by na ně narazila |
  | tag skupiny (`ProcessReaper.kill` při stop skupiny) | `chrome_crashpad_handler` mimo session (7). V testech skončil sám, tag je pojistka, když ne (zpracování dumpu po skutečném pádu). A procesy, které aplikace spouští sama s `group.environment` |
  | strom (`descendants()` v `stopGracefully`) | wrapper bez `exec` (8.2 pokus 5), macOS bez `/proc`, potomci s vyčištěným env |
  | tag vlastníka (`reapOrphans`) | pád JVM |

  Na Linuxu je SIGKILL potomků ze stromu z velké části zdvojený s `Launched.killLeftovers()`, které běží hned po něm
  (potomci env dědí). Odstranit ho ale nejde bez ztráty macOS a procesů s vyčištěným env.
- **Bod 2 z 8.6 (process group pro graceful stop, 2.12) nedělat.** LibreOffice SIGTERM dostane i dnes (2), `.lock`
  zůstává i při SIGTERM přímo `soffice.bin` (4) a Chromium si potomky ukončí samo (8, 9). Úklid `.lock`
  v `Segment.prepare` konzumenta zůstává nutný bez ohledu na způsob zastavení.
- **Výměna `INSTANCE_ENV` za pgid** by případ (6) zvládla stejně, ale nic by neodstranila (tag kvůli crashpad a pádu
  JVM zůstává) a přidala by `setsid`, spouštění `kill` (procps nebo `bash`) a ochranu proti recyklaci pgid.
- **Neověřeno:** Chromium se sandboxem (zařízení s userns) a skutečný pád (SIGSEGV, crashpad zapisuje dump).
  Obnova LibreOffice po pádu: (5) ukázal, že `oosplash` po SIGKILL `soffice.bin` nerestartuje.
