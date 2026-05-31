## O co jde

`klib` je multimodulová Kotlin/Spring Boot **knihovna** (ne aplikace) — sada utilit rozdělená do nezávisle publikovatelných modulů, aby každý projekt mohl záviset jen na tom, co potřebuje. Publikuje se do GitHub Packages pod group `io.github.petrbalat`, základní balíček `cz.petrbalat.klib`.

Technologie: Kotlin 2.3.0, Java 25 (source/target), Spring Boot 4.0.0 / Spring 7 / Spring Security 7, Gradle 9.4.1 (Kotlin DSL), JUnit 5. Kotlin compiler běží s `-Xjsr305=strict -Xjvm-default=all -progressive`.

> Pozn.: `.claude/project-info.md` je podrobná referenční dokumentace po modulech, ale je částečně zastaralá (uvádí Java 21 / Gradle 8.14.4). Při nesouladu věř `build.gradle.kts`.

## Příkazy

```bash
./gradlew build                      # build + testy všeho
./gradlew :jdk:build                 # build jednoho modulu
./gradlew test                       # všechny testy
./gradlew :jdk:test                  # testy jednoho modulu
./gradlew :jdk:test --tests "*AlphanumComparatorTest"   # jedna testovací třída
./gradlew publish                    # publikace všech modulů do GitHub Packages
./gradlew :jdk:publish               # publikace jednoho modulu
./gradlew clean
```

Publikace čte token z `ossrhPassword` v `gradle.properties` (v gitignore, není commitnutý). Bez něj heslo spadne na `"Unknown password"` a publikace selže.

## Architektura

### Graf závislostí modulů
- **`jdk`** — základní čistě JVM utility (datetime, collections, io, http, delegate cache, math, string, ssl, xml). Skoro všechny závislosti jsou `compileOnly` (coroutines, jackson, slf4j), aby si konzumenti přinesli vlastní verze. Většina Spring modulů závisí na `jdk`.
- Spring moduly (`spring-boot-web`, `spring-boot-jwt`, `spring-boot-mail`, `spring-boot-image`, `spring-boot-graphql`) — každý závisí na `:jdk` plus na příslušném Spring starteru.
- Samostatné moduly — `geom` (PostGIS / lat-long + Jackson serializery), `retrofit2` (interceptory), `pdf`, `qr`, `ftp`.

### Konvence, které dodržuje každý modul (vzorem je `jdk/build.gradle.kts`)
- Aplikuje pluginy `org.springframework.boot` + `kotlin("jvm")`, ale **`bootJar` je vypnutý a obyčejný `jar` zapnutý** — jde o knihovny, ne spustitelné aplikace.
- Každý modul registruje task `sourcesJar` a publikaci `mavenJava`, pak volá `publishingKlib(this)`.
- Moduly používající Spring navíc aplikují `kotlin("plugin.spring")`.
- Těžké/volitelné integrace bývají `compileOnly` (např. `spring-boot-starter-web` v jwt), aby si je konzument zapnul podle potřeby.

### buildSrc (sdílená build logika)
- `buildSrc/src/main/kotlin/klibDsl.kt` — `Project.publishingKlib()` konfiguruje repozitář GitHub Packages (`maven.pkg.github.com/petrbalat/klib`, uživatel `petrbalat`, heslo z property `ossrhPassword`). Publikaci měň zde, ne v jednotlivých modulech.
- `buildSrc/src/main/kotlin/version.kt` — `KlibVersions`, sdílené konstanty verzí.
- Verze projektu je v kořenovém `build.gradle.kts` (`allprojects { version = ... }`); pro release ji navyš zde.

### Přidání nového modulu
1. Vytvoř `<name>/build.gradle.kts` podle vzoru existujícího modulu (zapni `jar`, vypni `bootJar`, registruj `sourcesJar` + publikaci `mavenJava`, zavolej `publishingKlib(this)`).
2. Přidej název modulu do seznamu `include(...)` v `settings.gradle.kts`.
3. Zdroje umísti do `<name>/src/main/kotlin/cz/petrbalat/klib/...`.
