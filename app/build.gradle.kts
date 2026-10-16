import org.gradle.api.tasks.testing.Test
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Preserve the editable JSON byte-for-byte while shipping only its deterministic gzip asset. */
@CacheableTask
abstract class CompressMedicationDictionary : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun compress() {
        val destination = outputDirectory.file("medication-dictionary.json.gzip").get().asFile
        // This directory belongs only to this task; discard obsolete generated formats.
        check(destination.parentFile.deleteRecursively())
        check(destination.parentFile.mkdirs())
        sourceFile.get().asFile.inputStream().use { input ->
            object : GZIPOutputStream(destination.outputStream()) {
                init { def.setLevel(Deflater.BEST_COMPRESSION) }
            }.use { output -> input.copyTo(output) }
        }
    }
}

val compressMedicationDictionary = tasks.register<CompressMedicationDictionary>("compressMedicationDictionary") {
    sourceFile.set(layout.projectDirectory.file("src/main/dictionary/medication-dictionary.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/medicationDictionaryAssets"))
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(compressMedicationDictionary, CompressMedicationDictionary::outputDirectory)
}

android {
    namespace = "io.github.sibmaks.app.dose"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.sibmaks.app.dose"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // All picker languages must be available offline, including Play app-bundle installs.
    bundle { language { enableSplit = false } }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
}

// Catalog-only edits regenerate the shipped asset and rerun its validation/search tests.
tasks.withType<Test>().configureEach {
    dependsOn(compressMedicationDictionary)
    val source = layout.projectDirectory.file("src/main/dictionary/medication-dictionary.json")
    val asset = compressMedicationDictionary.flatMap { it.outputDirectory.file("medication-dictionary.json.gzip") }
    inputs.file(source).withPropertyName("medicationDictionary").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(asset).withPropertyName("compressedMedicationDictionary").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("dose.dictionary.source", source.asFile.absolutePath)
    systemProperty("dose.dictionary.asset", asset.get().asFile.absolutePath)
}
