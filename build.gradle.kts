plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin").version("0.1.0")
    id("com.gradleup.shadow").version("9.2.0")
    kotlin("jvm")
}

group = "doom.despair"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jmdns:jmdns:3.6.3")
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}
javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}
application {
    mainClass.set("doom.despair.MainKt")
}
tasks.named<Jar>("jar") {
    enabled = false
}
tasks.named<Jar>("shadowJar") {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "doom.despair.MainKt"
    }
}
tasks.named("startScripts") {
    dependsOn("shadowJar")
}
tasks.named("distZip") {
    dependsOn("shadowJar")
}
tasks.named("distTar") {
    dependsOn("shadowJar")
}
tasks.named("assemble") {
    dependsOn("shadowJar")
}
tasks.register<Exec>("buildAndRunJar") {
    group = "application"
    description = "Builds the shadow jar and runs it."
    dependsOn("shadowJar")
    doFirst {
        val jarFile = tasks.named<Jar>("shadowJar").get().archiveFile.get().asFile
        commandLine("java", "-jar", jarFile.absolutePath)
    }
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

val generateBuildConstants by tasks.registering {
    group = "build"
    description = "Generates build constants with the project version for Kotlin and TS codebases"
    
    val versionStr = project.version.toString()
    inputs.property("version", versionStr)
    
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z0-9.\-]+))?""").find(versionStr)
    val major = match?.groupValues?.get(1)?.toInt() ?: 1
    val minor = match?.groupValues?.get(2)?.toInt() ?: 0
    val patch = match?.groupValues?.get(3)?.toInt() ?: 0
    val prerelease = match?.groupValues?.get(4) ?: ""
    
    val kotlinFile = file("src/main/java/doom/despair/core/BuildConstants.kt")
    val frontendFile = file("frontend/src/buildConstants.ts")
    val lobbyFile = file("lobby/buildConstants.ts")
    
    outputs.files(kotlinFile, frontendFile, lobbyFile)
    
    val tripleQuote = "\"\"\""
    
    doLast {
        kotlinFile.parentFile.mkdirs()
        kotlinFile.writeText("""
            package doom.despair.core

            object BuildConstants {
                const val VERSION = "$versionStr"
                const val MAJOR = $major
                const val MINOR = $minor
                const val PATCH = $patch
                const val PRERELEASE = "$prerelease"
                /**
                    Check if current version is equal to other version string.
                    @returns 0 if wrong version, 1 if snapshot, 2 if correct
                */
                fun sameVersion(otherVersion: String): Int {
                    val match = Regex(${tripleQuote}^(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z0-9.\-]+))?${tripleQuote}).find(otherVersion) ?: return 0
                    val oMajor = match.groupValues[1].toInt()
                    val oMinor = match.groupValues[2].toInt()
                    val oPatch = match.groupValues[3].toInt()
                    if (oMajor == MAJOR && oMinor == MINOR && oPatch == PATCH) {
                        return if (PRERELEASE == match.groupValues[4]) 2 else 1
                    }
                    return 0;
                }
            }
        """.trimIndent() + "\n")
        
        val tsContent = """
            export const VERSION = "$versionStr";
            export const MAJOR = $major;
            export const MINOR = $minor;
            export const PATCH = $patch;
            export const PRERELEASE = "$prerelease";

            export function sameVersion(otherVersion: string): number {
                const match = /^(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z0-9.\-]+))?/.exec(otherVersion);
                if (!match || match.length < 4) return 0;
                const oMajor = parseInt(match[1]!, 10);
                const oMinor = parseInt(match[2]!, 10);
                const oPatch = parseInt(match[3]!, 10);
                if (MAJOR === oMajor && MINOR === oMinor && PATCH === oPatch) {
                    return PRERELEASE === match[4] ? 2 : 1;
                }
                return 0
            }
        """.trimIndent() + "\n"
        
        frontendFile.parentFile.mkdirs()
        frontendFile.writeText(tsContent)
        
        lobbyFile.parentFile.mkdirs()
        lobbyFile.writeText(tsContent)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildConstants)
}