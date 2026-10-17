plugins {
    java
    id("net.neoforged.moddev") version "2.0.148"
}

version = property("mod_version")!!
group = property("mod_group_id")!!

base {
    archivesName = "atomicannon"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://repo.warfactory.co/releases") {
        name = "Warfactory"
        content {
            includeGroup("com.hbm")
            includeGroup("dev.engine_room")
        }
    }
}

// Runtime-only dependencies for dev runs; not part of the published mod.
val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(localRuntime) }

dependencies {
    // NTM is optional at runtime; we only reference its multiblock API through a guarded compat class.
    compileOnly("com.hbm:ntm-next-neoforge:${property("ntm_version")}") { isTransitive = false }
    compileOnly("org.jspecify:jspecify:1.0.0")
    // WarForge (your 26.2 port) is optional: claims are checked through a guarded compat class.
    compileOnly(files("libs/warforge-26.2.jar"))

    // Dev-only: lets the game test server load real NTM machines. Never bundled into our jar.
    // -PwithoutNtm: run with neither NTM nor WarForge, to check the mod works on its own.
    if (!project.hasProperty("withoutNtm")) {
        "localRuntime"("com.hbm:ntm-next-neoforge:${property("ntm_test_version")}") { isTransitive = false }
        "localRuntime"("dev.engine_room:crankshaft-neoforge:1.6.0+mc26.2") { isTransitive = false }
        "localRuntime"(files("libs/warforge-26.2.jar", "libs/ldlib2-26.2.jar"))
    }
    // -PwithMafglib: load mafglib (bundles Foxified Networking, which breaks NeoForge FakePlayers). Client runs only.
    if (project.hasProperty("withMafglib"))
        "localRuntime"(files("libs/mafglib-26.2.jar", "libs/foxified-classtweaker.jar"))
}

neoForge {
    version = property("neo_version") as String

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
            programArgument("--nogui")
            gameDirectory = file("run/server")
        }
        // Visual check of the schematic hologram: opens run/saves/preview_test, screenshots, quits.
        create("previewTest") {
            client()
            systemProperty("schematicannon.previewTest", "true")
            programArguments.addAll("--quickPlaySingleplayer", "preview_test")
        }
        // Game tests on a headless server. -Pstress adds the server load test (reports tick times).
        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = file("run/gametest")
            jvmArgument("-Xmx4G")
            if (project.hasProperty("stress"))
                systemProperty("schematicannon.stress", "true")
        }
    }

    mods {
        create(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

val modProps = mapOf(
    "mod_id" to property("mod_id"),
    "mod_name" to property("mod_name"),
    "mod_version" to property("mod_version"),
    "minecraft_version" to property("minecraft_version"),
    "neo_version" to property("neo_version"),
)

tasks.processResources {
    inputs.properties(modProps)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(modProps)
    }
}
