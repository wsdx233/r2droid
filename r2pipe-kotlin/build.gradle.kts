plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
