plugins {
    java
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "org.saintqd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven(url = "https://mvn.lumine.io/repository/maven-public/")
    maven(url = "https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven(url = "https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly(files("../VineriumLib/build/libs/VineriumLib-1.0-SNAPSHOT.jar"))

    compileOnly("io.lumine:Mythic-Dist:5.+")
    compileOnly("me.clip:placeholderapi:2.+") // repo.extendedclip.com
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18-SNAPSHOT")
}

tasks {

    shadowJar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/*.kotlin_module")

        archiveFileName.set("${project.name}-$version.jar")
    }

    build {
        dependsOn(shadowJar)
    }
}

tasks.withType<Jar> {

    // To avoid the duplicate handling strategy error
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // To add all the dependencies otherwise a "NoClassDefFoundError" error
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}