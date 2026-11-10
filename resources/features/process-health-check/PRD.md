# Kontrola zdraví spravovaných procesů (health check)

**Status**: Návrh — implementace později
**Modul**: `spring-boot-process`
**Autor**: Petr Balat
**Datum**: 2026-11-10
**Související**: CDC `resources/features/client-process-resilience.md` — nález K2 a fáze 6 (tento dokument rozpracovává
jejich část pro klib a v detailech API je nahrazuje), nález K1 (politika restartů, viz [Návaznosti](#návaznosti))

---

## Přehled

### Stručný popis
Watchdog `ManagedProcess` dnes pozná jen to, že proces **skončil**. Tento feature přidává volitelnou kontrolu zdraví:
aplikace k procesu připojí sondu, watchdog ji pravidelně volá a proces, který opakovaně neodpovídá nebo nepostupuje,
ukončí a restartuje stejnou cestou jako po pádu. Součástí jsou obecné sondy bez spolupráce aplikace (aktivita souboru,
spotřeba CPU) a integrační testy, které zatuhnutí simulují na skutečných procesech.

### Motivace
- Chromium po pádu rendereru („Aw, Snap“) nebo zaseknutém JavaScriptu dál žije — obrazovka stojí, watchdog nic nevidí.
- Přehrávač se zamrzlým streamem, proces zablokovaný na mrtvém síťovém mountu nebo v deadlocku je pro watchdog zdravý.
- LibreOffice zaseknutý při startu na dialogu se hlásí jako `Running`, i když nikdy nic nezobrazí.
- Kiosk nikdo ručně neopraví; bez detekce zůstane obsah stát do konce plánu.

### Cíle
- Detekce zatuhnutí u dlouho běžících procesů i u segmentů cyklu, se zabitím a restartem přes stávající `RestartPolicy`.
- Rozlišení příčiny restartu (pád × zatuhnutí × selhané spuštění) ve stavu, událostech a metrikách.
- Signál „proces je připraven“ (první úspěšná sonda), který dnes chybí.
- Bez health checku se chování knihovny nemění.

### Mimo rozsah
- Sondy pro konkrétní aplikace (CDP pro Chromium, IPC pro mpv) — patří do konzumenta (CDC `client/`), klib dodá mechanismus.
- Kontrola **obsahu** (stránka zobrazuje chybu sítě, černý obraz u zdravého procesu).
- `runProcess` — má vlastní `timeout`.
- Nové vlastnosti `klib.process.*` — parametry kontroly se liší podle sondy, výchozí hodnoty jsou v kódu.

---

## Analýza

### Jak to funguje dnes
- `ManagedProcess.Run.watch` čeká na `proc.onExit().await()` wrapperu `unshare` (`ManagedProcess.kt`, `awaitExit`).
  Wrapper končí s hlavním příkazem (PID 2 v namespace; `tini -g` předá jeho kód). Nic jiného se nehlídá.
- `Launched.awaitStarted` při startu jen ověří, že `tini` má potomka (do 5 s).
- `SegmentCyclePlayer.playSegment` zkrátí segment jen při `GaveUp`, tedy opět při konci procesu.
- `isAlive` = žije wrapper.

### Nálezy

| # | Nález | Dopad |
|---|---|---|
| H1 | `Running` znamená „forknuto“, ne „připraveno“ | start zaseknutý na dialogu vypadá jako zdravý běh; konzument nemá na co navázat akce po startu |
| H2 | Smrt nebo zamrznutí pomocného procesu v namespace není vidět (renderer, GPU proces) | „Aw, Snap“ do konce plánu |
| H3 | JVM nemá k procesu kanál — stdin je zavřený, výstup jde přes OS redirect | kontrola musí jít bokem: soubor, `/proc`, socket |
| H4 | Exit kód po zabití je 137/143 | zabití kvůli zatuhnutí by bez dalšího pole nešlo odlišit od OOM killeru |
| H5 | Segmenty cyklu se ze zatuhnutí léčí samy, nejpozději po délce segmentu | priorita je u dlouho běžících procesů; u segmentů jde o zkrácení černé pauzy |

K H3: namespace odděluje jen PID, mount a user. Síť je sdílená (localhost TCP — CDP), souborový strom je stejný
(unix socket — mpv IPC) a `ProcessHandle.descendants()` z JVM vidí celý strom namespace včetně CPU času.

### Varianty

| Varianta | Pro | Proti |
|---|---|---|
| A) Sonda ve watchdogu klib: nezdravý proces watchdog zabije a pokračuje běžnou cestou restartu | jedno místo; platí pro `ManagedProcess`, skupiny i segmenty; počítadlo restartů, události a metriky zůstávají jednotné | změna jádra knihovny |
| B) Konzument si proces hlídá sám a volá `stop()` + `start()` | bez změny klib | druhá vrstva restartů mimo `RestartPolicy`; každý konzument řeší souběh se `stop()` znovu; segmenty to neumí |
| C) Jen obecné heuristiky v klib (CPU, výstup), bez sond aplikace | nulová práce pro konzumenta | „Aw, Snap“ ani zaseknutou stránku nepozná; falešné poplachy u statického obsahu |

Doporučení: **A**, s obecnými sondami z C jako hotovými stavebními kameny.

### Jak poznat zatuhnutí

Univerzální způsob neexistuje. Sondy jsou dvou druhů a obě se převedou na stejné rozhraní:

| Druh | Otázka | Příklady |
|---|---|---|
| odpovídá | „Reaguje proces na dotaz?“ | CDP `Runtime.evaluate("1")`, HTTP `/json/version`, připojení na socket |
| postupuje | „Změnilo se něco od minulé kontroly?“ | `time-pos` z mpv IPC, velikost logu, CPU čas stromu procesů |

---

## Specifikace

### Dotčené soubory (`spring-boot-process/src/main/kotlin/cz/petrbalat/klib/process/`)

| Soubor | Změna |
|---|---|
| `HealthCheck.kt` (nový) | `HealthCheck`, `HealthProbe`, `RunProbe`, `ProcessRun`, `ProcessHealth` |
| `HealthProbes.kt` (nový) | `responds`, `progresses`, `fileActivity`, `cpuActivity` |
| `HealthTracker.kt` (nový) | internal počítání prahů, bez procesů a času (vzor `RestartBudget`) |
| `ProcessSpec.kt` | `health: HealthCheck? = null` |
| `ManagedProcess.kt` | monitor ve watchdogu, `health: StateFlow<ProcessHealth>`, `ExitCause` v `Restarting` / `GaveUp` / `GiveUp` |
| `ProcessDsl.kt` | `CommonProcessBuilder.healthCheck(…)` — platí pro proces skupiny i segment |
| `ProcessGroup.kt` | `GiveUp` s příčinou |
| `cycle/SegmentCyclePlayer.kt` | jen text logu podle příčiny |
| `autoconfigure/ProcessGaveUpEvent.kt`, `ProcessEventPublisher.kt` | `ProcessRestartedEvent.cause` |
| `autoconfigure/MicrometerProcessMetrics.kt` | tag `cause` u `klib.process.restarts` |

### Veřejné API

```kotlin
/** Kontrola zdraví procesu — součást [ProcessSpec]; null = jen hlídání konce procesu jako dosud. */
class HealthCheck(
    val probe: HealthProbe,
    /** prodleva mezi sondami (od konce jedné do začátku další); první sonda po [every] od startu */
    val every: Duration = 10.seconds,
    /** sonda bez výsledku do limitu = neúspěch */
    val timeout: Duration = 5.seconds,
    /** neúspěchů v řadě → proces je nezdravý */
    val failures: Int = 3,
    /**
     * Rozběh: do prvního úspěchu se neúspěchy nepočítají. Bez úspěchu do [startup] od startu je proces nezdravý.
     * [Duration.ZERO] = počítá se hned, [Duration.INFINITE] = na první úspěch se čeká bez limitu.
     */
    val startup: Duration = 30.seconds,
)

/** Pro každý běh procesu (i po restartu) vzniká nová [RunProbe] — smí držet stav běhu (minulý vzorek). */
fun interface HealthProbe {
    fun attach(run: ProcessRun): RunProbe
}

fun interface RunProbe {
    /** true = zdravý; false, výjimka nebo překročení [HealthCheck.timeout] = neúspěch */
    suspend fun check(): Boolean
}

/** Jeden běh procesu, jak ho vidí sonda. */
class ProcessRun internal constructor(
    val spec: ProcessSpec,
    /** wrapper `unshare`; strom namespace = `process.descendants()` */
    val process: ProcessHandle,
    val restarts: Int,
) {
    val pid: Long
    val uptime: Duration
}

sealed interface ProcessHealth {
    /** bez health checku, před startem, mezi restarty, po zastavení */
    data object Unchecked : ProcessHealth
    /** běží, sonda ještě neuspěla */
    data object Starting : ProcessHealth
    data object Healthy : ProcessHealth
    /** neúspěchy v řadě, zatím pod prahem */
    data class Failing(val failures: Int) : ProcessHealth
}

enum class ExitCause { Exited, LaunchFailed, Unhealthy }
```

Validace v `HealthCheck.init`: `every > 0`, `timeout > 0`, `failures >= 1`, `startup >= 0`. `HealthCheck` není data class
(nese lambdu); `toString` vypíše jen parametry.

Změny stávajících typů — nové parametry jsou poslední a mají výchozí hodnotu, zdrojová kompatibilita zůstává:

```kotlin
data class ProcessSpec(…, val gracefulStop: Duration = 5.seconds, val health: HealthCheck? = null)

data class Restarting(val restarts: Int, val exitCode: Int?, val cause: ExitCause = ExitCause.Exited) : ProcessState
data class GaveUp(val restarts: Int, val cause: ExitCause = ExitCause.Exited) : ProcessState
class GiveUp(val spec: ProcessSpec, val restarts: Int, val cause: ExitCause = ExitCause.Exited)
class ProcessRestartedEvent(…, val exitCode: Int?, val cause: ExitCause = ExitCause.Exited)

class ManagedProcess(…) {
    val health: StateFlow<ProcessHealth>
}
```

`exitCode` zůstává skutečný kód wrapperu: `Exited` → kód procesu, `LaunchFailed` → null (jako dnes), `Unhealthy` → kód
po zabití (143 / 137).

### Hotové sondy (`HealthProbes.kt`)

```kotlin
/** „odpovídá“ — bez stavu */
fun responds(check: suspend (ProcessRun) -> Boolean): HealthProbe

/** „postupuje“ — vzorek se musí lišit od předchozího vzorku téhož běhu; první vzorek je úspěch, null neúspěch */
fun progresses(sample: suspend (ProcessRun) -> Any?): HealthProbe

/** soubor se od minulé sondy změnil (délka nebo čas změny); zkrácení při rotaci je také změna, chybějící soubor neúspěch */
fun fileActivity(file: File): HealthProbe

/**
 * Strom procesů namespace spotřeboval od minulé sondy aspoň [atLeast] CPU času. První sonda běhu je úspěch.
 * Heuristika jen pro procesy, které pracují trvale (přehrávání videa) — statický obsah má legitimně nulové CPU.
 */
fun cpuActivity(atLeast: Duration = 10.milliseconds): HealthProbe
```

- `progresses` s `every = 5 s`, `failures = 4` znamená „20 s bez změny“. Zvláštní parametr pro dobu stání není.
- `cpuActivity` sčítá `ProcessHandle.Info.totalCpuDuration()` přes `run.process.descendants()`. Rozdíl se počítá
  **po PID** — konec potomka nesmí součet snížit, nový potomek se počítá celou svou hodnotou.
- Výchozí `atLeast` (jeden tik) pozná jen úplné zamrznutí (deadlock, SIGSTOP, nepřerušitelné I/O). Vyšší práh pozná
  i zamrzlý stream u přehrávače, musí se ale naladit na zařízení.

### DSL (`CommonProcessBuilder` — proces skupiny, `processSpec { }` i `segment { }`)

```kotlin
var health: HealthCheck? = null

fun healthCheck(
    probe: HealthProbe,
    every: Duration = 10.seconds, timeout: Duration = 5.seconds, failures: Int = 3, startup: Duration = 30.seconds,
)

/** zkratka pro `healthCheck(responds(check), …)` */
fun healthCheck(
    every: Duration = 10.seconds, timeout: Duration = 5.seconds, failures: Int = 3, startup: Duration = 30.seconds,
    check: suspend (ProcessRun) -> Boolean,
)

/** `fileActivity` nad logem procesu; bez `logTo` / `logToFile` → IllegalStateException při sestavení specu */
fun outputActivity(): HealthProbe
```

```kotlin
group.start("chromium") {
    command("chromium", "--kiosk", …)
    logToFile()
    healthCheck(every = 10.seconds, startup = 60.seconds) { cdp.evaluate("1") == 1 }
}

group.start("mpv") {
    command("mpv", "--input-ipc-server=$socket", url)
    healthCheck(progresses { mpvIpc.timePos(socket) }, every = 5.seconds, failures = 4)
}

segment(30.seconds) {
    command("mpv", "--fs", file.path)
    healthCheck(cpuActivity(atLeast = 200.milliseconds), every = 5.seconds, failures = 2, startup = 10.seconds)
}
```

### Chování watchdogu

Monitor je dítě korutiny watchdogu a žije jen po dobu jednoho běhu procesu:

```kotlin
// Run.watch — místo dnešního proc.awaitExit()
private suspend fun Process.awaitEnd(restarts: Int): ExitCause = coroutineScope {
    // doběhne jen tehdy, když proces prohlásí za nezdravý a ukončí ho; jinak ho po konci procesu zrušíme
    val monitor: Deferred<Unit>? = spec.health?.let { check -> async { monitorHealth(check, restarts) } }
    onExit().await()
    val unhealthy = monitor?.isCompleted == true && !monitor.isCancelled
    monitor?.cancel()
    if (unhealthy) ExitCause.Unhealthy else ExitCause.Exited
}
```

1. Start běhu: `health = Starting`, `probe.attach(ProcessRun)` vytvoří novou `RunProbe`.
2. Smyčka: `delay(every)` → sonda s limitem `timeout` → `HealthTracker.record(success, uptime)` → zápis do `health`.
3. Výsledek `Unhealthy`: log WARN s důvodem (počet neúspěchů, poslední chyba, nebo „nerozběhl se do `startup`“),
   `launched.terminate(spec.gracefulStop)` a monitor skončí.
4. Watchdog se probudí na konci procesu a pokračuje **stávající cestou**: `budget.resetIfStable(ran)` → `budget.next()` →
   `Restarting(restarts, exitCode, cause = Unhealthy)` → `delay` → nový běh. S vyčerpaným limitem nebo
   `RestartPolicy.NEVER` → `GaveUp(restarts, cause = Unhealthy)`.
5. `Restarting`, `GaveUp`, `Stopped` → `health = Unchecked`.

`HealthTracker` (internal, čistá logika):

| Vstup | Stav před | Výsledek |
|---|---|---|
| úspěch | cokoli | `Healthy`, řada neúspěchů = 0, rozběh končí |
| neúspěch | zatím žádný úspěch, `uptime < startup` | `Starting` (nepočítá se) |
| neúspěch | zatím žádný úspěch, `uptime >= startup` | nezdravý — nerozběhl se |
| neúspěch | už byl úspěch, řada + 1 < `failures` | `Failing(řada)` |
| neúspěch | už byl úspěch, řada + 1 = `failures` | nezdravý — neodpovídá |

Doba do zásahu: nejvýš `failures × (every + timeout)`, pak `gracefulStop` a prodleva restartu. S výchozími hodnotami
30–45 s.

### Pravidla pro sondy

- Sonda běží na `Dispatchers.IO` **mimo strukturu watchdogu** (stejně jako `detached` v `SegmentCyclePlayer`) — nikdy
  pod zámkem `ManagedProcess` / `ProcessGroup` a nikdy na vlákně, ze kterého se volá `onStateChange`.
- Po `timeout` se sonda zruší a počítá jako neúspěch. Blokující sonda, která na zrušení nereaguje, doběhne stranou;
  dokud běží, **nová se nespustí** a každý interval je neúspěch (bez hromadění vláken). Log WARN jednou za běh.
- Výjimka ze sondy je neúspěch, watchdog kvůli ní neskončí. `CancellationException` se nepolyká.
- `stop()` a zrušení scope monitor zruší; výsledek rozběhnuté sondy se zahodí a `stop()` na ni nečeká.
- Mezi restarty a po `GaveUp` / `Stopped` se sonda nevolá.
- Sonda proces neřídí (žádný `stop()` / `start()`), jen vrací výsledek.

### Ukončení nezdravého procesu

`terminate(spec.gracefulStop)`: SIGTERM vnitřnímu `tini`, po lhůtě SIGKILL wrapperu a tím celého namespace.
Chromium s mrtvým rendererem SIGTERM zpracuje a uklidí profil; úplně zamrzlý proces ho nezpracuje a restart se
zpozdí o `gracefulStop` (viz otevřené otázky). Nepřibývá žádný zámek: zabití jde přes stejný `Launched` jako `stop()`
a souběžné volání `terminate` je neškodné.

Souběh pádu a verdiktu sondy ve stejný okamžik může pád označit jako `Unhealthy` — neškodné, restart proběhne stejně.

### Stav, události, metriky

- `health` je **samostatný** `StateFlow`, ne součást `ProcessState`. Přepnutí `Healthy` ↔ `Failing` tak nevolá
  `onStateChange`, nemění stav skupiny a nespouští znovu kolektory čekající na `Running` (CDC fáze 1, N4: přihlášení
  po každém novém `Running` přes `collectLatest`).
- `health.first { it == ProcessHealth.Healthy }` je signál „proces je připraven“ (H1).
- Skupina a registr: beze změny rozhraní, `Restarting.cause` a `GiveUp.cause` projdou stávajícími háčky
  (`onRestarting`, `onGaveUp`, `ProcessGroupsListener`).
- `ProcessRestartedEvent.cause`; nová událost nevzniká.
- Micrometer: `klib.process.restarts` dostane tag `cause` (`exited` / `launch_failed` / `unhealthy`), tři předem
  registrovaná počítadla. Součet přes tag odpovídá dnešní hodnotě.
- Logy anglicky: první úspěch a návrat z `Failing` INFO, neúspěch po rozběhu WARN, během rozběhu DEBUG.

### Segmenty cyklu

`health` cestuje ve `ProcessSpec`; `playSegment` dělá `spec.copy(restart = NEVER)`, kontrola tedy zůstane. Nezdravý
segment → `GaveUp(cause = Unhealthy)` → stávající větev „skončil dřív“ → další segment (nejdřív po `minSegment`).
Mění se jen text logu (`exited` × `unhealthy`).

### Návaznosti

- **K1 (politika restartů, CDC fáze 3)** — nezávislé, na pořadí implementace nezáleží. Platí ale:
  - restart kvůli zatuhnutí čerpá stejné počítadlo a stejnou rostoucí prodlevu jako pád;
  - `ProcessRecoveredEvent` (stabilní běh vyhodnocený za běhu) se u procesu s health checkem smí poslat jen ve stavu
    `Healthy` — jinak konzument smaže chybu u zamrzlého přehrávače.
- **CDC fáze 6** — klient doplní sondy: Chromium přes `CdpClient` (odpověď na `Runtime.evaluate`, událost
  `Inspector.targetCrashed`), mpv přes `--input-ipc-server` (`time-pos` přes `progresses`; ne u obrázků a pauzy).
  Oproti náčrtu v CDC dokumentu: pole se jmenuje `cause` a `exitCode` zůstává skutečný.
- **Dokumentace po implementaci** — `spring-boot-process/README.md` (popis, diagram, příklad), `AGENTS.md` modulu
  (stav a události, testy), `docs/project-info.md`; navýšit verzi v kořenovém `build.gradle.kts`.

---

## Testy

Styl beze změny: skutečné procesy, reálný čas, krátké intervaly (`every = 100 ms`, `timeout = 200 ms`,
`gracefulStop = 150 ms`), čekání přes `StateFlow.await { }`. Jeden test do 2 s.

### Bez procesů (běží všude, i na hostu)

**`HealthTrackerTest`**
- úspěch nuluje řadu neúspěchů
- `failures` neúspěchů v řadě → nezdravý; o jeden méně ne
- neúspěchy během rozběhu před prvním úspěchem se nepočítají
- bez úspěchu do `startup` → nezdravý
- po prvním úspěchu se počítá i uvnitř okna rozběhu
- `startup = ZERO` počítá od první sondy, `INFINITE` nikdy nevyprší
- neplatné parametry `HealthCheck` vyhodí `IllegalArgumentException`

**`HealthProbesTest`**
- `progresses`: první vzorek úspěch, stejný neúspěch, změna úspěch, null neúspěch
- `progresses`: nový běh (`attach`) nezná vzorek předchozího běhu
- `fileActivity`: růst → úspěch, beze změny → neúspěch, zkrácení → úspěch, chybějící soubor → neúspěch

**`ProcessDslTest`** — `healthCheck` v `processSpec { }`, ve skupině i v `segment { }`; `outputActivity()` bez logu selže.

### Mechanika watchdogu — `ManagedProcessHealthTest` (`@RequiresPidNamespace`, `dockerTest`)

Proces `sleep 1000`, sonda řízená z testu (`AtomicBoolean`, počítadla volání):

| Test | Ověřuje |
|---|---|
| zdravý proces se nerestartuje | po N sondách stále `Running(restarts = 0)`, `health = Healthy` |
| nezdravý proces watchdog zabije a restartuje | `Restarting(1, cause = Unhealthy)` → `Running(restarts = 1)`, starý strom nežije (`forker.sh`) |
| ojedinělý neúspěch pod prahem nezabije | střídání false / true, žádný restart, `health` projde `Failing(1)` |
| neúspěchy během rozběhu nezabijí | sonda začne vracet true před `startup`, restart nepřijde |
| proces, který se nerozběhne do `startup`, se restartuje | sonda stále false |
| visící sonda je neúspěch | blokující sonda ignorující zrušení → restart; souběžně běží nejvýš jedna; `stop()` se vrátí hned |
| výjimka ze sondy je neúspěch | watchdog běží dál, po prahu restart |
| `stop()` během sondy | `Stopped`, žádný restart, sonda se už nezavolá |
| `RestartPolicy.NEVER` + nezdravý | `GaveUp(cause = Unhealthy)`, `onStateChange` právě jednou, namespace uklizený |
| nezdravý proces ignorující SIGTERM | `ignore-term.sh` → eskalace na SIGKILL po `gracefulStop`, pak restart |
| každý běh má vlastní sondu | `attach` voláno `restarts + 1`×, `ProcessRun.restarts` a `pid` odpovídají stavu |
| pád procesu s health checkem | `cause = Exited`, monitor nezasáhne |
| průběh `health` | `Unchecked` → `Starting` → `Healthy` → `Failing` → `Unchecked` při `Restarting` |

### Skutečné zatuhnutí (`dockerTest`)

Nová fixture `heartbeat.sh` — přehrávač s tepem:

```sh
#!/bin/sh
# přehrávač s tepem — řádek na výstup každých 50 ms; zamrznutí simuluje test přes SIGSTOP
while true; do echo beat; sleep 0.05; done
```

- **`zamrzlý proces (SIGSTOP) pozná outputActivity a watchdog ho restartuje`** — proces s `logTo` a `outputActivity()`;
  test pošle `kill -STOP` hlavnímu procesu namespace (přímý potomek `tini`; pomůcka `namespaceMain` bez názvu binárky —
  `sh` je v Debianu `dash`). Proces žije (`isAlive`), tep ustane → `Restarting(cause = Unhealthy)` → nový běh tepe,
  `health = Healthy`. Zastavený proces SIGTERM nezpracuje, test tak ověří i eskalaci na SIGKILL.
- **`cpuActivity`** — `sleep 1000` se restartuje, zaneprázdněná smyčka (`while :; do :; done`) ne. Okno aspoň 1 s kvůli
  zatíženému CI agentovi.

**Předpoklad k ověření jako první krok:** `kill -STOP` z JVM na PID uvnitř namespace projde. Očekává se, že ano —
testy už dnes těm samým PID posílají SIGTERM a SIGKILL (`init.destroy()`, `browser.destroyForcibly()`), mapování
`--map-current-user` nechává procesům uid 1000. Záložní varianta bez signálu: fixture přestane tepat, když test
vytvoří spouštěcí soubor, a test ho při `Restarting` smaže.

### Skupina, cyklus, autokonfigurace

- **`ProcessGroupTest`** — `onRestarting` a listener registru dostanou `cause = Unhealthy`; `onGaveUp` nese `GiveUp.cause`.
- **`SegmentCyclePlayerTest`** — zamrzlý segment skončí před svou délkou (ne dřív než `minSegment`) a cyklus pokračuje
  dalším segmentem; sleduje se přes `nowPlaying`.
- **`ProcessAutoConfigurationTest`** — `ProcessRestartedEvent.cause`; počítadlo `klib.process.restarts` s tagem `cause`.

### Skutečné Chromium — `ChromiumProcessTest` (`@RequiresDockerTestImage`)

- **`zamrzlý browser proces pozná sonda na DevTools a watchdog Chromium restartuje`** — sonda čte port
  z `DevToolsActivePort` a volá `GET http://127.0.0.1:<port>/json/version` (JDK `HttpClient` s limitem). Test pošle
  `kill -STOP` browser procesu → restart se stejným profilem → nový `DevToolsActivePort`. Starý port po restartu
  odmítá spojení; to spadá do rozběhu a nepočítá se.
- **Volitelně** — zamrzlý (`kill -STOP`) nebo zabitý (`kill -9`) renderer: sonda přes CDP WebSocket
  (`Runtime.evaluate`) napsaná v testu nad JDK `WebSocket`. Ověřuje scénář „Aw, Snap“; sonda sama patří do klienta,
  proto jen pokud se vejde bez nové závislosti.

### Co integrační testy neověří (ruční ověření na RPi)

- zamrzlý Wayland / GPU a černý obraz u procesu, který odpovídá;
- skutečný výpadek RTSP na síti a chování mpv při něm;
- práh `cpuActivity` pro přehrávání s hardwarovým dekódováním;
- míra falešných poplachů při dlouhém provozu.

---

## Implementační poznámky

- Monitor musí zachytit všechny výjimky sondy; neošetřená by zrušila `coroutineScope` běhu a watchdog by skončil
  stavem `Stopped`.
- `ProcessSpec` je data class a `health` nese lambdu — rovnost specu se u ní řídí identitou `HealthCheck`. Segmenty
  smějí sdílet stejnou instanci napříč cykly, stav běhu drží až `RunProbe`.
- `ProcessSpec.toString` vypíše parametry kontroly, ne sondu.
- Wrapper, který neskončí ani po SIGKILL, watchdog dál čeká na jeho konec (dnes totéž loguje `killWrapper` jako ERROR).
- Stabilní běh (`resetIfStable`) se u nezdravého běhu měří stejně jako u pádu, celou dobou běhu. Doba zamrznutí je
  proti `stableRun` zanedbatelná.
- Proces v nepřerušitelném I/O (mrtvý CIFS mount) po SIGKILL namespace zmizí až s návratem I/O; wrapper skončí hned,
  restart na něj nečeká.
- Zpětná kompatibilita: bez `health` se nespouští žádná korutina navíc a `Restarting` / `GaveUp` mají `cause = Exited`
  nebo `LaunchFailed` podle dnešního rozlišení `exitCode == null`.

---

## Otevřené otázky

- [ ] **Lhůta při zabití nezdravého procesu** — `spec.gracefulStop` (návrh; Chromium uklidí profil), nebo rovnou SIGKILL
  (úplně zamrzlý proces SIGTERM stejně nezpracuje a restart se zdrží o lhůtu)? Případně vlastní parametr v `HealthCheck`.
- [ ] **`cpuActivity` v první verzi** — zařadit hned, nebo až po naladění prahu na RPi?
- [ ] **Výchozí hodnoty v `klib.process.health.*`** — návrh: ne, parametry se liší podle sondy.
- [ ] **Sondy na socket v klib** (`tcpConnect`, unix socket) — návrh: zatím ne, konzument má vlastní klienty (CDP, IPC).
- [ ] **Zdraví ve stavu skupiny nebo vlastní událost při `Failing`** — návrh: ne, stačí `ManagedProcess.health`
  a `cause` u restartu.
- [ ] **Pořadí vůči K1** — dělat před politikou restartů, nebo po ní (pak rovnou s pravidlem pro `ProcessRecoveredEvent`)?

---

## Historie změn

### 2026-11-10
- Vytvoření dokumentu z analýzy `spring-boot-process` (čtení kódu, bez spuštění testů)
