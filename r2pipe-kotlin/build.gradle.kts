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
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
