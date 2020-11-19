plugins {
    idea
    kotlin("jvm") version "1.4.10"
    kotlin("plugin.serialization") version "1.4.10"
}

group = "dev.twelveoclock"
version = "1.0.0"

repositories {

    jcenter()
    mavenLocal()
    mavenCentral()

    maven("https://maven.pkg.jetbrains.space/camdenorrb/p/twelveoclock-dev/maven") {
        name = "Camden's"
    }
}

dependencies {


    implementation(kotlin("stdlib-jdk8"))
    implementation("org.capnproto:runtime:0.1.5")
    implementation("me.camdenorrb:Netlius:1.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1")

    testImplementation(kotlin("test-junit"))
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "14"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "14"
    }
    wrapper {
        gradleVersion = "6.7.1"
    }
}