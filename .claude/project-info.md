# klib - Projekt informace

## Základní informace
- **Název**: klib
- **Verze**: 2.0.0
- **Typ**: Multi-modul Kotlin/Spring Boot knihovna
- **Repository**: https://github.com/petrbalat/klib
- **Autor**: Petr Balát (petrbalat)

## Technologie
- **Kotlin**: 2.3.0
- **Java**: 21 (source & target compatibility)
- **Spring Boot**: 4.0.0
- **Spring Framework**: 7.x
- **Spring Security**: 7.x (s lambda DSL)
- **Gradle**: 8.14.4 (Kotlin DSL)
- **Kotlinx Coroutines**: 1.6.4
- **Build system**: Gradle s Kotlin DSL
- **Jakarta EE**: 11 (Servlet 6.1)

## Struktura modulů

### jdk
Základní utility pro práci s JDK
- **Balíčky**:
  - `datetime` - LocalDate/LocalDateTime utility, progrese, intervaly, formátování, svátky
  - `collections` - collection utility
  - `io` - file utils, network utils, stream utils
  - `http` - HTTP client utility
  - `delegate` - SimpleCacheDelegate, JsonFileDbDelegate, CacheJsonFileDbDelegate
  - `coroutines` - coroutine utility
  - `math` - BigDecimal utility, BD helpers
  - `string` - string utils, AlphanumComparator
  - `ssl` - EmptyX509TrustManager
  - `xml` - XML utility
  - `concurrent` - concurrency utility
- **Závislosti**: minimální (compileOnly: coroutines, jackson, slf4j)

### spring-boot-web
Spring MVC utility
- **Soubory**:
  - `springUtils.kt` - Spring core utility
  - `modelUtils.kt` - práce s modely
  - `requestUtils.kt` - HTTP request utility
  - `multipartFileUtils.kt` - MultipartFile utility
- **Závislosti**: jdk modul, spring-boot-starter-web, kotlin-reflect

### spring-boot-jwt
JWT autentizace pro Spring Security
- **Hlavní komponenty**:
  - `JwtUtil` - generování a validace JWT tokenů (JJWT 0.11.5)
  - `JWTAuthenticationFilter` - autentizační filtr
  - `JWTAuthorizationFilter` - autorizační filtr
  - `JwtMvcSecurityConfiguration` - Spring Security konfigurace
  - `JacksonSerializerDeserializer` - serializace UserDetails
  - `AuthResponse` - DTO pro autentizační odpověď
- **Konfigurace** (application.properties):
  ```
  jwt.token.secret=super strong secret
  jwt.token.clazz=package.name.UserExtendUserDetail
  jwt.token.expirationDateTime=2d #optional
  ```
- **Závislosti**: jdk modul, spring-boot-starter-security, jjwt-api/impl/jackson
- **Dokumentace**: `JWT_README.md`

### spring-boot-mail
Email služba
- **Hlavní třída**: `SmtpEmailService` - implementace `EmailService`
- **Features**:
  - MIME messages s HTML podporou
  - Přílohy
  - Konfigurovatelný from/personal
  - Logging odesílání
- **DTO**: `EmailDto`, `EmailAttachment`
- **Poznámka**: Bez async (změna v 1.3.7)
- **Závislosti**: jdk modul, spring-boot-starter-mail

### spring-boot-image
Správa a zpracování obrázků
- **Interface**: `ImageService`
- **Implementace**:
  - `FileSystemImageService` - lokální filesystem storage
  - `FtpImageService` - FTP storage
- **Features**:
  - Upload obrázků
  - Automatický resize (Imgscalr, max 1920px FIT_TO_WIDTH)
  - WebP konverze (cwebp utility)
  - URL generování
- **DTO**: `ImageDto`
- **Config**: `ImageConfig`
- **Poznámka**: Bez suspend (změna v 1.3.6)
- **Závislosti**: jdk modul, spring-boot-starter-web, imgscalr, jackson

### spring-boot-graphql
GraphQL konfigurace
- **Hlavní třída**: `GraphqlConfig` - Spring konfigurace
- **Features**: `LocalDateTimeCoarcing` - custom scalar pro LocalDateTime
- **Závislosti**: spring-boot-starter-graphql

### pdf
PDF generování
- **Hlavní třída**: `PdfService`
- **Závislosti**: pravděpodobně iText nebo podobná knihovna

### qr
QR kódy
- **Hlavní třída**: `QRService`
- **Závislosti**: pravděpodobně ZXing

### geom
Geometrie a GIS utility
- **Features**:
  - PostGIS utility (`postgisUtils.kt`)
  - LatLong DTO (`LatLongDto`)
  - GeoPoint Jackson serializers/deserializers
  - Konstanty pro GIS výpočty
- **Module**: `GeoPointModule` pro Jackson

### ftp
FTP client utility
- **Hlavní třída**: `FTPClientUtils`
- **Závislosti**: Apache Commons Net

### retrofit2
Retrofit2 interceptors
- **Třídy**:
  - `AuthInterceptor` - token autentizace
  - `BasicAuthInterceptor` - basic auth
  - `RetrofitLoggerInterceptor` - logování
  - `retroUtils.kt` - utility funkce

## Build & Publishing

### Gradle konfigurace
- **Root projekt**: `build.gradle.kts` - společná konfigurace pro všechny moduly
- **BuildSrc**: vlastní DSL a verze
  - `klibDsl.kt` - `publishingKlib()` funkce
  - `version.kt` - `KlibVersions` objekt
- **Settings**: `settings.gradle.kts` - seznam modulů

### Publishing
- **Repository**: GitHub Packages (`maven.pkg.github.com/petrbalat/klib`)
- **Credentials**:
  - Username: `petrbalat`
  - Password: token z `gradle.properties` (`ossrhPassword`)
- **Group**: `io.github.petrbalat`
- **Artifacts**: JAR + sources JAR (signing zakomentováno)

### Run konfigurace (.run/)
- `CLEAN.run.xml`
- `publish.run.xml` - hlavní publish
- Individual modul publish konfigurace (např. `klib_jwt [bintrayUpload].run.xml`)

## Historie verzí (poslední změny)

### 2.0.0 (current) - BREAKING CHANGES
- **Spring Boot 4.0.0** upgrade
- **Spring Framework 7.x** a **Spring Security 7.x**
- **Gradle 8.14.4** upgrade
- **Jakarta EE 11** (Servlet 6.1)
- **GraphQL**: package změna z `o.s.b.autoconfigure.graphql` → `o.s.b.graphql.autoconfigure`
- **Security**: Lambda DSL syntax pro konfiguraci (`.exceptionHandling { }` místo `.exceptionHandling()`)
- **Security**: AuthenticationSuccessHandler s FilterChain parametrem
- **Web**: ModelAndView null-safety improvements
- **Test**: Migration z WebFlux na MVC v testech

### 1.4.0
- Spring Boot 3.5.9 upgrade

### 1.3.7
- EmailService bez async

### 1.3.6
- ImageService bez suspend

### 1.3.5
- Odstranění webflux

### 1.3.2
- Fix JwtUtil

## Git info
- **Main branch**: master
- **Remote**: GitHub
- **Untracked files**: `spring-boot-image/c:\home\easyrent\resources/` (Windows path issue)

## Poznámky k projektu
- Všechny moduly používají **Java 21** a **Kotlin 2.3.0**
- Spring Boot moduly mají **bootJar disabled**, jen běžné JAR (knihovna, ne aplikace)
- **Kotlin compiler args**: `-Xjsr305=strict`, `-Xjvm-default=all`, `-progressive`
- **Test framework**: JUnit 5 (Jupiter)
- Některé závislosti jsou `compileOnly` pro flexibilitu použití
- Projekt postupně migruje z webflux na běžný Spring MVC
