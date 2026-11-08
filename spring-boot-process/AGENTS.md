# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Platí pro modul `spring-boot-process`; obecné konvence repozitáře jsou v kořenovém `CLAUDE.md`. Uživatelský
přehled API, vlastnosti `klib.process.*` a omezení (jen Linux, `sudo`, init v kontejneru) jsou v `README.md`.

## Příkazy

```bash
./gradlew :spring-boot-process:test                                  # na hostu; bez PID namespace (macOS) se namespace testy přeskočí
./gradlew :spring-boot-process:test --tests "*ManagedProcessTest"    # jedna třída
./gradlew :spring-boot-process:dockerTest                            # všechny testy v Linux kontejneru
./gradlew :spring-boot-process:build                                 # check → spustí i dockerTest, je-li Docker
```

`dockerTest` (`buildSrc/src/main/kotlin/dockerTest.kt`) zkompiluje třídy na hostu, zesynchronizuje je s runtime
classpath do `build/docker-test/cp` a v kontejneru pustí JUnit `ConsoleLauncher --scan-class-path` — nelze tedy
filtrovat přes `--tests`, běží vždy všechno. Před spuštěním `dockerTestPreflight` ověří, že Docker runtime povolí
user/PID namespace; pokud ne (např. AppArmor/userns omezení na CI agentovi), vypíše varování s výstupem `unshare`
a `dockerTest` přeskočí — s `-Pklib.process.requirePidNamespace=true` build selže. Reporty:
`build/test-results/dockerTest`; bez dostupného Dockeru se task přeskočí.
Dockerfile a bezpečnostní volby napodobují CDC: Debian trixie, OpenJDK 25, `pi` uid 1000, skupiny zařízení,
`tini -s`, `SYS_ADMIN`, `seccomp=unconfined`, `systempaths=unconfined` a `apparmor=unconfined` (`docker-default`
  profil má `deny mount`); bez `--privileged` a hostitelských cgroups.

Každý `ManagedProcess` i segment cyklu používá povinně `unshare --user --map-current-user --pid --fork --kill-child --mount-proc`
a vnitřní `/usr/bin/tini -g`. Vyžaduje Linux, util-linux, `tini` a povolený user/PID namespace. Stop posílá SIGTERM
vnitřnímu `tini`, po `gracefulStop` zabije wrapper; `Duration.ZERO` zabije wrapper hned. Debian util-linux 2.41.5
SIGTERM wrapperu ignoruje. Konec hlavního procesu uklidí namespace potomky; pád JVM sám wrapper nezastaví, dokud
neskončí kontejner. `--mount-proc` dává namespace vlastní `/proc` (PID v `/proc` odpovídají `getpid()`); vyžaduje
`systempaths=unconfined`, mount namespace je private (pozdější mounty kontejneru proces neuvidí).

Autokonfigurace po startu volá `checkPidNamespaceSupport()` a při selhání jen loguje ERROR (AOT trénink v `docker build`
má výchozí seccomp a `unshare --user` tam neprojde).

`runProcess` je ve výchozím stavu přímý proces mimo namespace pro `sudo`/mount příkazy a spravuje pouze jeho hlavní
PID; `isolated = true` použije stejný `launch` jako `ManagedProcess` a timeout/zrušení zabije wrapper (`--kill-child`).
`ProcessGroup` už netaguje env ani neprohledává `/proc`; `owner` v Springu je jen pro metriky a události.
`PidNamespaceManagedProcessTest` ověřuje produkční cestu přes `ManagedProcess`, signály, eskalaci, restart a úklid
odpojených potomků; `UnshareProcessTest` doplňuje kontext a chování jádra. Testovací fixture `root:video` je čitelný
i při výpisu nemapované skupiny jako `nogroup`. Skutečná zařízení a Wayland ověřil provoz na RPi s CDC (2026-11-08):
video (mpv), Chromium i LibreOffice se v namespace přehrávají.

## Architektura

Tři vrstvy; Spring zná jen `autoconfigure`:

- **Jádro (`cz.petrbalat.klib.process`, bez Springu)** — `ProcessSpec` (neměnný popis procesu), `ManagedProcess`
  (watchdog), `RestartBudget`, `ProcessGroup`, `ProcessGroups` (továrna + registr, `ProcessGroupsListener`),
  top-level `runProcess`, `ProcessOutput` + `rotateLog`; všechny spravované procesy běží v PID namespace.
  DSL: `ProcessDsl.kt` (`command`, `sudo`, `processSpec`, buildery) a `processGroupDsl.kt` (`group.start(name) { }`,
  `withGroup`, `processGroups { }`).
- **`autoconfigure`** (jen s `klib.process.owner`) — `ProcessProperties` (`klib.process.*`), bean `ProcessGroups`
  s `ProcessEventPublisher` (listener → `ProcessRestartedEvent` / `ProcessGaveUpEvent`), `MicrometerProcessMetrics`.
  Registrace v `META-INF/spring/...AutoConfiguration.imports`.
- **`cycle` / `interval`** — `SegmentCyclePlayer` (+ `segment { }`, `group.playCycle { }`) a `IntervalScheduler`
  (+ `schedule(id, start, end) { }`, `EndReason`) stojí nad jádrem, ale autokonfigurace je nezná (`IntervalScheduler`
  si konzument deklaruje jako `@Bean`).

### Tok zastavení
`ProcessGroup.stop()` → zruší navázané cykly segmentů (`attach`, `SegmentCyclePlayer` s `group`) a počká na ně — jinak
by po zastavení procesů spustily další segment; zevnitř cyklu (kontext `GroupWork`, např. `whilePlaying`) nečeká, čekal
by sám na sebe → `ManagedProcess.stop()` zruší watchdog → `Launched.terminate` pošle SIGTERM vnitřnímu `tini -g`,
čeká `gracefulStop` a při timeoutu zabije wrapper `unshare`. Jádro při smrti PID 1 uklidí celý namespace včetně
odpojených potomků. `Duration.ZERO` zabije wrapper bez SIGTERM.

`runProcess` bez `isolated` namespace nepoužívá: při timeoutu/zrušení ukončí jen přímý proces. Potomci, kteří drží
zděděnou rouru, mohou přežít `OUTPUT_GRACE`; výstup se vrátí bez dalšího úklidu. S `isolated` skončí namespace
s příkazem, takže roura dostane EOF hned.

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
- Watchdog startuje s `CoroutineStart.ATOMIC` (aby `finally` proběhl i ve zrušeném scope, ale callback neběžel pod zámky startu), úklid ve `finally`
  běží v `NonCancellable`, délka běhu se měří `TimeSource.Monotonic`. V blocích `catch (e: Throwable)` se
  `CancellationException` vždy znovu vyhazuje.
- KDoc a komentáře česky, log zprávy a texty výjimek anglicky.

## Testy

- Testy spouštějí **skutečné procesy v reálném čase** s krátkými zpožděními; virtual time (`runTest`) se záměrně
  nepoužívá (zdůvodnění v KDoc `IntervalSchedulerTest`).
- Pomůcky v `src/test/.../testUtils.kt`: `StateFlow.await { }` místo pollingu stavů, `waitUntil { }` pro `/proc`
  a soubory, `fixture(name)` vrací cestu ke skriptu ze `src/test/resources/fixtures` — spouští se vždy jako
  `listOf("sh", fixture("x.sh"))`, takže na spustitelných právech po kopírování resources nezáleží.
- Fixtures simulují konkrétní chování: `forker.sh` (Chromium s renderery), `ignore-term.sh` (ignoruje SIGTERM),
  `crash-loop.sh` (pád s kódem 3), `chatty.sh` (výstup > 64 KB).
- `ChromiumProcessTest` a `LibreOfficeProcessTest` (`@RequiresDockerTestImage`) spouštějí skutečné aplikace z image
  s volbami CDC, jen `--headless`: stop přes SIGTERM bez eskalace, úklid zygote/rendereru a `oosplash`/`soffice.bin`,
  restart po pádu se starými zámky. Zjištěné chování: v PID namespace ProcessSingleton Chromia nezastaví druhou
  instanci nad stejným profilem (souběžná Chromia potřebují vlastní `--user-data-dir`); první start nového profilu
  LibreOffice spouští `soffice.bin` dvakrát (PID až po načtení dokumentu); `.~lock.<dokument>#` vzniká i s `--view`,
  zámek z jiného profilu headless start ukončí kódem 0.
- Podmínky v `testConditions.kt`: test, který spouští spravovaný proces (`ManagedProcess`, `start`, segment cyklu,
  `runProcess` s `isolated`), má `@RequiresPidNamespace` (běží jen tam, kde projde `checkPidNamespaceSupport()`);
  test závislý na testovacím image (uživatel `pi`, skupiny zařízení) má `@RequiresDockerTestImage`
  (`KLIB_PROCESS_DOCKER_TEST=true` z Dockerfile), `sudo` test `@EnabledIf("...#sudoAvailable")`. Ostatní testy
  (DSL, `runProcess` bez `isolated`, binding autokonfigurace, stop cyklu) běží bez podmínky i na macOS.
  `@EnabledOnOs(OS.LINUX)` nepoužívej — namespace testy ověř přes `dockerTest`.
- `UnshareProcessTest` ověřuje chování namespace; `PidNamespaceManagedProcessTest` testuje přímo povinnou produkční
  cestu `ManagedProcess`, bez zvláštní proměnné prostředí nebo DSL přepínače.
  `ProcessGroup` má internal konstruktor; testy ho vytvářejí přímo s `object : ProcessGroupListener {}`, nebo přes
  `ProcessGroups()` / `processGroups { }` bez Springu.
- `IntervalScheduler` s posunutými hodinami: `IntervalSchedulerClockTest` (`MovableClock` — skok NTP, letní čas).
  `SegmentCyclePlayerTest` sleduje přehrání přes `nowPlaying` kolektorem na `Dispatchers.Unconfined` (nic nepřeskočí).
- Autokonfigurace se testuje přes `ApplicationContextRunner` s `klib.process.owner` (bez něj se autokonfigurace nezapne).
- Testy s procesy mají vlastní `CoroutineScope(SupervisorJob() + Dispatchers.IO)` a v `@AfterEach` zastaví procesy
  a scope zruší.
