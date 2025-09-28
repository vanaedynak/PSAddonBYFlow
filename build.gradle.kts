import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
}

group = "dev.byflow"
version = "0.2.0"

tasks.wrapper {
    gradleVersion = "8.8"
    distributionType = Wrapper.DistributionType.BIN
}

val paperApiVersion = "1.21.1-R0.1-SNAPSHOT"
val nbtApiVersion = "2.12.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://nexus.sirblobman.xyz/public/") {
        name = "sirblobman"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "codemc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("de.tr7zw:item-nbt-api-plugin:$nbtApiVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("PSAddonBYFlow")
    archiveVersion.set(project.version.toString())
}

tasks.named<ProcessResources>("processResources") {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(mapOf(
            "name" to "PSAddonBYFlow",
            "version" to project.version
        ))
    }
}
