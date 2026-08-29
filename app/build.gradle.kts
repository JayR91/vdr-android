plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jayr91.vdr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jayr91.vdr"
        minSdk = 26
        // Google Play requires API 36 for new submissions from 31 Aug 2026.
        targetSdk = 36
        versionCode = 20
        versionName = "1.6.2"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // No abiFilters: with FFmpeg gone the app ships no native libraries at
        // all, so every ABI is served by the same bytecode. Filtering here used
        // to shrink the FFmpeg kit; keeping it now would only drop x86_64
        // emulator and Chromebook support for nothing.
    }

    val keystoreProps = rootProject.file("keystore.properties")
    if (keystoreProps.exists()) {
        val props = keystoreProps.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val i = line.indexOf("=")
                line.substring(0, i).trim() to line.substring(i + 1).trim()
            }
        signingConfigs {
            create("release") {
                storeFile = file(props.getValue("storeFile"))
                storePassword = props.getValue("storePassword")
                keyAlias = props.getValue("keyAlias")
                keyPassword = props.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 on, so the build emits mapping.txt. Play flags its absence as
            // "no deobfuscation file": without it, any crash or ANR report from
            // a user arrives as unreadable stack frames. AGP places the mapping
            // inside the bundle automatically, so uploading it is not a separate
            // step.
            //
            // Safe to turn on here: nothing in this app resolves types by name.
            // There is no Gson/Moshi/kotlinx-serialization -- the sidecar JSON is
            // written by hand through org.json with literal keys -- and no
            // Class.forName or reflective field access anywhere. Room and Compose
            // ship their own consumer rules.
            isMinifyEnabled = true
            // Deliberately NOT shrinking resources. It buys a little size and
            // risks stripping anything referenced indirectly; the warning being
            // fixed here is about the mapping file, not about size.
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // The bundle carries two small prebuilt AndroidX .so files
            // (graphics.path and datastore_shared_counter). Play asks for their
            // symbols so native crashes symbolicate; without this it reports
            // "no native debug symbols". FULL rather than SYMBOL_TABLE because
            // the libraries total well under 100 KB, so the extra size is
            // irrelevant and full frames are more useful.
            ndk {
                debugSymbolLevel = "FULL"
            }
            if (keystoreProps.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    // A transitive dependency drags in androidx.fragment 1.1.0, which predates
    // the ActivityResult APIs that MainActivity registers at construction.
    // Lint rates that combination Fatal, not cosmetic: the old fragment code
    // does not participate in the result registry, so the permission callbacks
    // never fire. Pin a version that has it.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Remuxing (mpegts→mp4, stream copy) uses the platform's own
    // MediaExtractor/MediaMuxer — see engine/Remuxer.kt. Deliberately no FFmpeg
    // dependency: dropping it saved ~15 MiB of native libs and the LGPL
    // relinking obligation those libraries carry.
    testImplementation("junit:junit:4.13.2")
    // On-device tests: Remuxer drives MediaExtractor/MediaMuxer, which only
    // exist on a real device, so its coverage has to be instrumented.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
