plugins {
    `java-library`
    kotlin("jvm") version "2.3.0"
}

group = "me.arcator"
version = "0.1"
description = "Alerts staff when they use commands normal players cannot"

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly("net.luckperms:api:5.5")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props =
            mapOf(
                "name" to project.name,
                "version" to project.version,
                "description" to project.description,
                "apiVersion" to "1.21",
            )
        inputs.properties(props)
        filesMatching("paper-plugin.yml") { expand(props) }
    }
}
