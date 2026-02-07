plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin").version("0.1.0")
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
}
javafx {
    version = "21"
}
tasks.test {
    useJUnitPlatform()
}