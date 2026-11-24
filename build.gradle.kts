plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.javaroot.antixray"
version = "1.0"

val targetJava = 25

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("dev.folia:folia-api:26.2.build.7-beta")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")

    implementation("org.xerial:sqlite-jdbc:3.54.1.0")

    testImplementation("dev.folia:folia-api:26.2.build.7-beta")
    testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(targetJava)
    }

    compileTestJava {
        options.encoding = "UTF-8"
        options.release.set(targetJava)
    }

    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveClassifier.set("thin")
    }

    shadowJar {
        archiveClassifier.set("")
        // sqlite-jdbc contains native JNI bindings tied to org.sqlite package names
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJar)
    }
}
