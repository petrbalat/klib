import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*

private val Project.`sourceSets`: org.gradle.api.tasks.SourceSetContainer
    get() =
        (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer

fun Project.publishingKlib(pe: PublishingExtension) {
    with(pe) {
        repositories {
            maven {
                url = uri("https://maven.pkg.github.com/petrbalat/klib")
                val ossrhPassword: String? by project
                credentials {
                    username = "petrbalat"
                    password = ossrhPassword ?: "Unknown password"
                }
            }
        }
    }
}
