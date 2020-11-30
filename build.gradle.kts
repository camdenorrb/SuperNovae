import java.io.FileInputStream
import java.util.*

plugins {
    java
    idea
    maven
    `maven-publish`
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
        name = "Camdenorrb"
    }
}

dependencies {


    implementation(kotlin("stdlib-jdk8"))
    implementation("org.capnproto:runtime:0.1.5")
    implementation("me.camdenorrb:Netlius:1.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    testImplementation(kotlin("test-junit"))
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks {

    val sourcesJar by creating(Jar::class) {
        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }
    val javadocJar by creating(Jar::class) {
        dependsOn(JavaPlugin.JAVADOC_TASK_NAME)
        archiveClassifier.set("javadoc")
        from(getByName("javadoc"))
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "14"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "14"
    }
    wrapper {
        gradleVersion = "6.7.1"
    }
    artifacts {
        add("archives", sourcesJar)
        add("archives", javadocJar)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
        }
    }
    repositories {
        maven {

            url = uri("https://maven.pkg.jetbrains.space/camdenorrb/p/twelveoclock-dev/maven")

            credentials {

                val secretProperties = Properties().apply {
                    load(FileInputStream("secret-gradle.properties"))
                }

                username = secretProperties["username"].toString()
                password = secretProperties["password"].toString()
            }

        }
    }
}