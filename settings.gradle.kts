pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
    }
}

plugins {
    id("gg.meza.stonecraft") version "1.12.+"
    id("dev.kikugie.stonecutter") version "0.9.+"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        fun fabric(minecraft: String) = version("$minecraft-fabric", minecraft)

        fabric("1.21.1")
        fabric("1.21.11")
        fabric("26.1")
        fabric("26.2")
        fabric("26.3")

        vcsVersion = "26.3-fabric"
    }
    create(rootProject)
}

include(":bukkit")
project(":bukkit").projectDir = file("platforms/bukkit")

rootProject.name = "anti-pie"
