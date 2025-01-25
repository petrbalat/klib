# klib
[![](https://jitpack.io/v/petrbalat/klib.svg)](https://jitpack.io/#petrbalat/klib)

## deploy

token z [token](https://github.com/settings/tokens) nastavit do gradle.properties ossrhPassword=ghp_6....


Gradle:
```
repositories {
    maven { setUrl("https://maven.pkg.github.com/petrbalat/klib") }
}

implementation("com.github.petrbalat.klib:jdk:VERSION")

```
