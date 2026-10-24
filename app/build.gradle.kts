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
        val destination =
            outputDirectory.file("medication-dictionary.json.gzip")
                .get().asFile
        // This directory belongs only to this task; discard obsolete generated formats.
        check(destination.parentFile.deleteRecursively())
        check(destination.parentFile.mkdirs())
        sourceFile.get().asFile.inputStream()
            .use { input ->
                object : GZIPOutputStream(destination.outputStream()) {
                    init {
                        def.setLevel(Deflater.BEST_COMPRESSION)
                    }
                }.use { output -> input.copyTo(output) }
            }
    }
}

val compressMedicationDictionary = tasks.register<CompressMedicationDictionary>("compressMedicationDictionary") {
    sourceFile.set(layout.projectDirectory.file("src/main/dictionary/medication-dictionary.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/medicationDictionaryAssets"))
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(
        compressMedicationDictionary,
        CompressMedicationDictionary::outputDirectory
    )
}

android {
    namespace = "io.github.sibmaks.app.dose"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.sibmaks.app.dose"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                providers.gradleProperty("RELEASE_STORE_FILE")
                    .get()
            )
            storePassword =
                providers.gradleProperty("RELEASE_STORE_PASSWORD")
                    .get()
            keyAlias =
                providers.gradleProperty("RELEASE_KEY_ALIAS")
                    .get()
            keyPassword =
                providers.gradleProperty("RELEASE_KEY_PASSWORD")
                    .get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
}

// Catalog-only edits regenerate the shipped asset and rerun its validation/search tests.
tasks.withType<Test>()
    .configureEach {
        dependsOn(compressMedicationDictionary)
        val source = layout.projectDirectory.file("src/main/dictionary/medication-dictionary.json")
        val asset = compressMedicationDictionary.flatMap { it.outputDirectory.file("medication-dictionary.json.gzip") }
        inputs.file(source)
            .withPropertyName("medicationDictionary")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(asset)
            .withPropertyName("compressedMedicationDictionary")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        systemProperty("dose.dictionary.source", source.asFile.absolutePath)
        systemProperty("dose.dictionary.asset", asset.get().asFile.absolutePath)
    }
