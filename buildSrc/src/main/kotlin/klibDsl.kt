
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getValue

private val Project.`sourceSets`: org.gradle.api.tasks.SourceSetContainer
    get() =
        (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer

fun Project.publishingKlib(pe: PublishingExtension) {
    with(pe) {
        publications {
            create<MavenPublication>("mavenPublication") {

                from(project.components["java"])

                val sourcesJar by tasks.creating(Jar::class) {
                    archiveClassifier.set("sources")

                    from(sourceSets.getByName("main").allSource)
                }

                artifact(sourcesJar)

                versionMapping {
                    usage("java-api") {
                        fromResolutionOf("runtimeClasspath")
                    }
                    usage("java-runtime") {
                        fromResolutionResult()
                    }
                }

                pom {
                    scm {
                        connection.set("scm:git:git://github.com/petrbalat/klib.git")
                        developerConnection.set("scm:git:https://github.com/petrbalat/klib.git")
                        url.set("https://github.com/petrbalat/klib")
                    }
                }
            }

            repositories {
                maven {
                    url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                }
            }
        }
    }
}
