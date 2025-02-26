import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import configurations.CompilerSettings.KOTLIN_JVM_TARGET
import configurations.FileUtils.symlink
import configurations.Frameworks.configureJUnit5
import configurations.Languages.attachRemoteRepositories
import configurations.ProjectVersions.TNOODLE_SYMLINK
import configurations.ProjectVersions.tNoodleImplOrDefault
import configurations.ProjectVersions.tNoodleVersionOrDefault

description = "An extension over the core server to provide a user-friendly UI. Also draws PDFs."

attachRemoteRepositories()

buildscript {
    repositories {
        maven(url = "$rootDir/gradle/repository")
    }

    dependencies {
        classpath(libs.wca.i18n)
    }
}

plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlin.serialization)
}

configurations {
    create("deployable") {
        extendsFrom(configurations["default"])
    }
}

dependencies {
    implementation(project(":tnoodle-server"))

    implementation(libs.zip4j)
    implementation(libs.markdownj.core)
    implementation(libs.itextpdf)
    implementation(libs.itext7)
    implementation(libs.batik.transcoder)
    implementation(libs.snakeyaml)
    implementation(libs.kotlin.argparser)
    implementation(libs.system.tray)
    implementation(libs.apache.commons.lang3)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.status.pages)

    runtimeOnly(libs.bouncycastle)

    "deployable"(project(":tnoodle-ui"))

    testImplementation(libs.mockk)
}

configureJUnit5()

kotlin {
    jvmToolchain(KOTLIN_JVM_TARGET)
}

application {
    mainClass.set("org.worldcubeassociation.tnoodle.server.webscrambles.WebscramblesServer")
}

tasks.create<JavaExec>("i18nCheck") {
    val ymlFiles = sourceSets.main.get().resources.matching {
        include("i18n/*.yml")
    }.sortedBy { it.nameWithoutExtension != "en" }

    mainClass.set("JarMain") // Warbler gives *fantastic* class names to the jruby bundles :/
    classpath = buildscript.configurations["classpath"]

    setArgs(ymlFiles)
}

tasks.getByName("check") {
    dependsOn("i18nCheck")
}

tasks.create("registerManifest") {
    tasks.withType<Jar> {
        dependsOn(this@create)
    }

    doLast {
        tasks.withType<Jar> {
            manifest {
                attributes(
                    "Implementation-Title" to project.tNoodleImplOrDefault(),
                    "Implementation-Version" to project.tNoodleVersionOrDefault()
                )
            }
        }
    }
}

tasks.getByName<ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations["deployable"])

    val targetLn = rootProject.file(TNOODLE_SYMLINK)
    outputs.file(targetLn)

    doLast {
        val targetFileAbs = archiveFile.orNull?.asFile
            ?.relativeToOrNull(rootProject.projectDir)

        val created = targetFileAbs?.let { symlink(targetLn, it) } ?: false

        if (!created) {
            logger.warn("Unable to (re-)create symlink for latest release! Using top-level Gradle tasks will implicitly reference an older build!")
        }
    }
}

tasks.getByName<JavaExec>("run") {
    args = listOf("--nobrowser")
    jvmArgs = listOf("-Dio.ktor.development=true")
}
