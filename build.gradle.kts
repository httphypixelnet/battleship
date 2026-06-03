plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin").version("0.1.0")
    id("com.gradleup.shadow").version("9.2.0")
    kotlin("jvm")
}

group = "doom.despair"
version = "1.0-SNAPSHOT"

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
    modules = listOf("javafx.controls")
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
    jvmToolchain(25)
}