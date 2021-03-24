plugins {
    application
    idea
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "6.1.0"
    kotlin("jvm") version "1.4.31"
    kotlin("plugin.serialization") version "1.4.31"
}

group = "dev.twelveoclock"
version = "1.0.55-Debug"

repositories {

    jcenter()
    mavenLocal()
    mavenCentral()

    maven("https://maven.pkg.jetbrains.space/camdenorrb/p/twelveoclock-dev/maven") {

        name = "TwelveOClockDev"

        credentials {
            project.properties["twelveoclockMavenUsername"]?.let { twelveoclockMavenUsername ->
                username = twelveoclockMavenUsername.toString()
            }
            project.properties["twelveoclockMavenPassword"]?.let { twelveoclockMavenPassword ->
                password = twelveoclockMavenPassword.toString()
            }
        }
    }
}

dependencies {

    implementation(kotlin("stdlib-jdk8"))
    //implementation("org.capnproto:runtime:0.1.5")
    implementation("me.camdenorrb:Netlius:1.0.12")
    implementation("org.jetbrains.kotlinx:atomicfu:0.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    implementation("me.camdenorrb:KCommons:1.2.1")
    implementation("commons-cli:commons-cli:1.4")

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
        kotlinOptions.useIR = true
        kotlinOptions.jvmTarget = JavaVersion.VERSION_15.majorVersion
        kotlinOptions.apiVersion = "1.4"
        kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=compatibility", "-Xmulti-platform", "-Xuse-experimental=kotlin.ExperimentalStdlibApi", "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes")
    }
    compileTestKotlin {
        kotlinOptions.useIR = true
        kotlinOptions.jvmTarget = JavaVersion.VERSION_15.majorVersion
        kotlinOptions.apiVersion = "1.4"
        kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=compatibility", "-Xmulti-platform", "-Xuse-experimental=kotlin.ExperimentalStdlibApi", "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes")
    }
    wrapper {
        gradleVersion = "6.8.3"
    }
    artifacts {
        add("archives", sourcesJar)
        add("archives", javadocJar)
    }
}

application {
    mainClassName = "dev.twelveoclock.supernovae.MainKt"
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
                project.properties["twelveoclockMavenUsername"]?.let { twelveoclockMavenUsername ->
                    username = twelveoclockMavenUsername.toString()
                }
                project.properties["twelveoclockMavenPassword"]?.let { twelveoclockMavenPassword ->
                    password = twelveoclockMavenPassword.toString()
                }
            }
        }
    }
}

repositories {

}