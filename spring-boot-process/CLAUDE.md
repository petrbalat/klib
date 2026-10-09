# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Platí pro modul `spring-boot-process`; obecné konvence repozitáře jsou v kořenovém `CLAUDE.md`. Uživatelský
přehled API, vlastnosti `klib.process.*` a omezení (jen Linux, `sudo`, init v kontejneru) jsou v `README.md`.

## Příkazy

```bash
./gradlew :spring-boot-process:test                                  # na hostu; na macOS se Linux testy přeskočí
./gradlew :spring-boot-process:test --tests "*ManagedProcessTest"    # jedna třída
./gradlew :spring-boot-process:dockerTest                            # všechny testy v Linux kontejneru
./gradlew :spring-boot-process:build                                 # check → spustí i dockerTest, je-li Docker
```

`dockerTest` (`buildSrc/src/main/kotlin/dockerTest.kt`) zkompiluje třídy na hostu, zesynchronizuje je s runtime
classpath do `build/docker-test/cp` a v kontejneru pustí JUnit `ConsoleLauncher --scan-class-path` — nelze tedy
filtrovat přes `--tests`, běží vždy všechno. Reporty: `build/test-results/dockerTest`. Bez dostupného Dockeru se
task tiše přeskočí. Image (`src/dockerTest/Dockerfile`) napodobuje produkční kontejner CDC klienta: Debian trixie,
OpenJDK 25, `tini` jako PID 1, uživatel uid 1000, `sudo` povolené jen na `/usr/bin/sleep`.

## Architektura

Tři vrstvy; Spring zná jen `autoconfigure`:

- **Jádro (`cz.petrbalat.klib.process`, bez Springu)** — `ProcessSpec` (neměnný popis procesu), `ManagedProcess`
  (watchdog), `RestartBudget`, `ProcessGroup`, `ProcessGroups` (továrna + registr, `ProcessGroupsListener`),
  `ProcessTagging`, `ProcessReaper`, top-level `runProcess`, `Process.stopGracefully`, `ProcessOutput` + `rotateLog`.
  DSL: `ProcessDsl.kt` (`command`, `sudo`, `processSpec`, buildery) a `processGroupDsl.kt` (`group.start(name) { }`,
  `withGroup`, `processGroups(owner) { }`).
- **`autoconfigure`** (jen s `klib.process.owner`) — `ProcessProperties` (`klib.process.*`), bean `ProcessGroups`
  s `ProcessEventPublisher` (listener → `ProcessRestartedEvent` / `ProcessGaveUpEvent`), `MicrometerProcessMetrics`.
  Registrace v `META-INF/spring/...AutoConfiguration.imports`.
- **`cycle` / `interval`** — `SegmentCyclePlayer` (+ `segment { }`, `group.playCycle { }`) a `IntervalScheduler`
  (+ `schedule(id, start, end) { }`, `EndReason`) stojí nad jádrem, ale autokonfigurace je nezná (`IntervalScheduler`
  si konzument deklaruje jako `@Bean`).

### Tok zastavení
`ProcessGroup.stop()` → zruší navázané cykly segmentů (`attach`, `SegmentCyclePlayer` s `group`) a počká na ně — jinak
by po zastavení procesů spustily další segment; zevnitř cyklu (kontext `GroupWork`, např. `whilePlaying`) nečeká, čekal
by sám na sebe → `ManagedProcess.stop()` (zruší coroutine watchdogu) → `finally` watchdogu v `NonCancellable`
volá `Launched.terminate` (`stopGracefully`: potomky sesbírá **před** `destroy()`, SIGTERM → `gracefulStop` → SIGKILL
rodiče, pak SIGKILL potomků; pak pozůstatky podle `INSTANCE_ENV`) → `ProcessReaper.kill(groupId)` dorazí procesy, které
se odpojily (`setsid`) a nejsou už ve stromu.

Potomky, které přežily pád rodiče (spadlý wrapper), strom už nevrátí. Každé spuštění proto nese env
`KLIB_PROCESS_INSTANCE=<uuid>` a watchdog je podle něj (`Launched.killLeftovers`, jen Linux) zabije před restartem,
v `finally` a v `stop()` po `GaveUp`. Při samotném `GaveUp` záměrně ne — odpojený launcher s `RestartPolicy.NEVER`.
`runProcess` při timeoutu i zrušení volá `Launched.terminate(Duration.ZERO)` (rovnou SIGKILL), po normálním konci jen
`killLeftovers` (potomek, který drží rouru výstupu); po timeoutu nečeká `waitFor()` bez limitu (proces v D stavu →
`exitValue = null`). Proces vzniká jen v `launch()` (`Launched.kt`: env volajícího, nové `INSTANCE_ENV`, pracovní
adresář) a jedno spuštění se zabíjí jen přes `Launched` — místo pro process group nebo izolaci
(`resources/features/refac-proces.md` kap. 7 a 8). Zabíjení podle env jde přes `killByEnv` (`ProcessReaper.kt`).

### Tagování
Každý proces skupiny dostane env `<envName>=<owner>/<groupId>` (výchozí `KLIB_PROCESS_TAG`), dědí ho i potomci.
`ProcessReaper` čte `/proc/<pid>/environ`; `killAllOwned` porovnává prefix `"$owner/"` (proto `owner` nesmí obsahovat
`/`). `ProcessGroups.reapOrphans()` (autokonfigurace ho volá při vytvoření beanu) zabije sirotky vlastníka z minulého
běhu JVM. Název procesu je ve skupině klíčem: běžící proces stejného názvu → `IllegalStateException`, proces po GaveUp /
stop nový stejného názvu nahradí (pozůstatky nejdřív zabije).

### Stav a události
Každé `ManagedProcess.start()` je samostatná inkarnace (`Run`: vlastní `Launched`, `gaveUp`, job) —
`finally` watchdogu sahá jen na svou, stav hlásí jen aktuální. Díky tomu smí `onStateChange` při `GaveUp` zavolat
i nový `start()`; `start()` běžícího procesu vyhodí `IllegalStateException`. Logika restartů je v `RestartBudget`.

`ManagedProcess` má jediný háček `onStateChange` (parametr konstruktoru); `setState` ho volá synchronně **před**
zápisem do `StateFlow` (nikdy pod zámkem) — kdo čeká na stav procesu, vidí už aktualizovaný stav skupiny. Řetěz:
`ManagedProcess` → `ProcessGroup` (mapa stavů; z `Restarting` / `GaveUp` odvodí `restarted` / `gaveUp` s `GiveUp`
internal `ProcessGroupListener`u, pak háčky procesu z DSL `onRestarting` / `onGaveUp`) → `ProcessGroups`
(`ProcessGroupsListener`; ve Springu `ProcessEventPublisher` → `publishEvent`). Eventy se publikují z vlákna watchdogu
(výjimečně z vlákna `start()`, pokud by proces skončil dřív, než watchdog poprvé suspenduje — `UNDISPATCHED`) a po
`ProcessGroup.stop()` už ne. Listener smí skupinu synchronně zastavit — příznak `gaveUp` se nastaví dřív než stav,
takže `ManagedProcess.stop()` watchdog jen zruší a nečeká na něj (jinak deadlock: čekal by sám na sebe).

### Vypnutí kontextu
`ProcessGroups` je `AutoCloseable` — Spring volá odvozenou destroy metodu `close()` (stejné pořadí jako
`DisposableBean`), záměrně ne `SmartLifecycle`: `Lifecycle.stop()` by běžel před `@PreDestroy` aplikace a zabil procesy
dřív, než je aplikace zastaví sama. `close()` je jen pojistka pro nezastavené skupiny, omezená `shutdownTimeout`.

### IntervalScheduler
`start`/`end` se převádí na `Instant` v zóně `clock` (letní čas) a čeká se po krocích `clockCheck` s novým čtením hodin
(skok NTP na zařízení bez RTC). `onEnd(EndReason)` se volá, jakmile `onStart` začal — i po jeho selhání / timeoutu
(`StartFailed`); `Shutdown` = vypnutí aplikace, plán pokračuje.

### Volitelný Micrometer
`micrometer-core` je `compileOnly`. Na Micrometer smí sahat jen `MicrometerProcessMetrics` a vnořená
`MetricsConfiguration` (`@ConditionalOnClass`) — bez Micrometeru na classpath se tak nenačtou. Metriky jsou jen
`@EventListener` na eventy skupin; `ProcessGroups` o nich neví. `@AutoConfiguration(afterName = ...)` uvádí `CompositeMeterRegistryAutoConfiguration`
v balíčku Boot 4 i Boot 3 — bez toho `@ConditionalOnBean(MeterRegistry)` bean nenajde.

## Konvence a invarianty

- `kotlinx-coroutines-core` je `api` (suspend/`CoroutineScope` ve veřejném API), `kotlin-reflect` je nutný pro
  constructor binding data class `ProcessProperties`. Na `:jdk` závisí `implementation` (`virtualThreadDispatcher`).
- `ProcessProperties` používá `java.time.Duration` kvůli bindingu (`5s`, `2m`); jádro pracuje s `kotlin.time.Duration`
  — převod je v `toDefaults()` / `toPolicy()`.
- DSL: buildery a `ProcessGroup` nesou `@ProcessDsl` (`@DslMarker`) — ve vnořeném bloku nejde implicitně volat
  vnější receiver (ani v implementaci: hodnotu vnějšího receiveru si ulož do proměnné). Buildery jen vytvářejí hodnoty
  (`ProcessSpec`, `Segment`), vedlejší efekt má jen funkce, která to říká (`start`, `withGroup`, `runProcess`,
  `playCycle`, `schedule`). Ke každému DSL existuje varianta s hodnotou (`start(spec)`, parametrový `runProcess`, konstruktor `Segment`)
  — kromě `IntervalScheduler.schedule`: varianta se třemi lambdami byla past (trailing lambda = `awaitReady`).
- Výstup `ManagedProcess` jde přes OS redirect (`Discard` / `appendTo` souboru s rotací), nikdy přes rouru do JVM —
  tu by nikdo nečetl a proces by se zablokoval. `runProcess` naopak čte výstup souběžně na virtuálních vláknech.
- Watchdog startuje s `CoroutineStart.UNDISPATCHED` (aby `finally` proběhl i ve zrušeném scope), úklid ve `finally`
  běží v `NonCancellable`, délka běhu se měří `TimeSource.Monotonic`. V blocích `catch (e: Throwable)` se
  `CancellationException` vždy znovu vyhazuje.
- KDoc a komentáře česky, log zprávy a texty výjimek anglicky.

## Testy

- Testy spouštějí **skutečné procesy v reálném čase** s krátkými zpožděními; virtual time (`runTest`) se záměrně
  nepoužívá (zdůvodnění v KDoc `IntervalSchedulerTest`).
- Pomůcky v `src/test/.../testUtils.kt`: `StateFlow.await { }` místo pollingu stavů, `waitUntil { }` pro `/proc`
  a soubory, `fixture(name)` vrací cestu ke skriptu ze `src/test/resources/fixtures` — spouští se vždy jako
  `listOf("sh", fixture("x.sh"))`, takže na spustitelných právech po kopírování resources nezáleží.
- Fixtures simulují konkrétní chování: `forker.sh` (Chromium s renderery), `detacher.sh` (`chrome_crashpad_handler` → `setsid`),
  `ignore-term.sh` (ignoruje SIGTERM), `crash-loop.sh` (pád s kódem 3), `chatty.sh` (výstup > 64 KB),
  `orphan-on-crash.sh` (wrapper spadne, potomek běží dál — `oosplash` → `soffice.bin`; PID zapíše do souboru z `$1`).
- Linux-only testy mají `@EnabledOnOs(OS.LINUX)`, případně `@EnabledIf("...#underInit")` (PID 1 je `tini`) nebo
  `"...#sudoAvailable"`. Na macOS se přeskočí — ověř je přes `dockerTest`.
- `ProcessReaper` má v konstruktoru `procDirectory` a `killer` — `ProcessReaperTest` staví falešný `/proc` v `@TempDir`.
  `ProcessGroup` má internal konstruktor; testy ho vytvářejí přímo s `object : ProcessGroupListener {}`, nebo přes
  `ProcessGroups(ProcessTagging("test"))` / `processGroups("test") { }` bez Springu.
- `IntervalScheduler` s posunutými hodinami: `IntervalSchedulerClockTest` (`MovableClock` — skok NTP, letní čas).
  `SegmentCyclePlayerTest` sleduje přehrání přes `nowPlaying` kolektorem na `Dispatchers.Unconfined` (nic nepřeskočí).
- Autokonfigurace se testuje přes `ApplicationContextRunner` s `klib.process.owner` (bez něj se autokonfigurace nezapne).
- Testy s procesy mají vlastní `CoroutineScope(SupervisorJob() + Dispatchers.IO)` a v `@AfterEach` zastaví procesy
  a scope zruší.
