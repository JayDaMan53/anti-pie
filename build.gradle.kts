import gg.meza.stonecraft.mod

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

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_anti-pie" }
    }
}
