plugins {
    `kotlin-dsl`
    `maven-publish`
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

repositories {
    jcenter()
}
