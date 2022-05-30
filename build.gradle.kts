plugins {
    application
    idea
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.39.0"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

group = "dev.twelveoclock"
version = "1.0.61-Debug"

repositories {
    jcenter()
    mavenLocal()
    mavenCentral()
}

dependencies {

    //implementation("org.capnproto:runtime:0.1.5")
    //implementation("me.camdenorrb:KCommons:1.2.1")

    implementation(kotlin("stdlib"))
    implementation("me.camdenorrb:Netlius:1.1.0")
    implementation("org.jetbrains.kotlinx:atomicfu:0.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    implementation("commons-cli:commons-cli:1.5.0")

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
        sourceCompatibility = JavaVersion.VERSION_16.toString()
        targetCompatibility = JavaVersion.VERSION_16.toString()
        kotlinOptions.jvmTarget = JavaVersion.VERSION_15.toString()
        kotlinOptions.apiVersion = "1.5"
        kotlinOptions.languageVersion = "1.5"
        kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=compatibility", "-Xmulti-platform", "-Xuse-experimental=kotlin.ExperimentalStdlibApi", "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes")
    }
    compileTestKotlin {
        sourceCompatibility = JavaVersion.VERSION_16.toString()
        targetCompatibility = JavaVersion.VERSION_16.toString()
        kotlinOptions.jvmTarget = JavaVersion.VERSION_15.toString()
        kotlinOptions.apiVersion = "1.5"
        kotlinOptions.languageVersion = "1.5"
        kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=compatibility", "-Xmulti-platform", "-Xuse-experimental=kotlin.ExperimentalStdlibApi", "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes")
    }
    artifacts {
        add("archives", sourcesJar)
        add("archives", javadocJar)
    }
}

application {
    mainClassName = "dev.twelveoclock.supernovae.MainKt"
}