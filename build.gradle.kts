import gg.meza.stonecraft.mod
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("gg.meza.stonecraft")
}

// Stonecraft normally supplies Fabric API for content mods. Anti Pie only uses
// Loader and vanilla server classes, so keep it off both the compile classpath
// and the published runtime metadata.
configurations.configureEach {
    exclude(group = "net.fabricmc.fabric-api")
}

val requiredJava = if (stonecutter.current.parsed >= "26.1") 25 else 21
val versionSpecificPlayerBlockEntities = buildList {
    if (stonecutter.current.parsed >= "1.21.11") {
        add("minecraft:shelf")
        add("minecraft:copper_golem_statue")
    }
    if (stonecutter.current.parsed < "26.2") add("minecraft:bed")
}.joinToString(separator = "", prefix = "") { "\n    \"$it\"," }
val testInstanceBlockEntity = if (stonecutter.current.parsed >= "1.21.11") {
    "\n    \"minecraft:test_instance_block\","
} else ""

modSettings {
    runDirectory = project.layout.buildDirectory.dir("run").get()
    testClientRunDirectory = project.layout.buildDirectory.dir("run-test-client").get()
    testServerRunDirectory = project.layout.buildDirectory.dir("run-test-server").get()
    variableReplacements = mapOf("javaVersion" to requiredJava)
}

stonecutter {
    replacements.string(current.parsed < "1.21.11") {
        replace("Identifier", "ResourceLocation")
    }
    replacements.string(current.parsed < "26.1") {
        replace("ChunkPos.pack(", "ChunkPos.asLong(")
        replace("forgetPacket.pos().x()", "forgetPacket.pos().x")
        replace("forgetPacket.pos().z()", "forgetPacket.pos().z")
    }
}

tasks.named<ProcessResources>("processResources") {
    filesMatching("default-anti-pie.json5") {
        expand(
            "versionSpecificPlayerBlockEntities" to versionSpecificPlayerBlockEntities,
            "testInstanceBlockEntity" to testInstanceBlockEntity
        )
    }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_anti-pie" }
    }
}
