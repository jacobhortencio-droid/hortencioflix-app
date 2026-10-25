plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// +100: o repositório público recomeça a contagem de compilações; a versão tem de continuar a subir (a última privada foi a 35)
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt() + 100

android {
    namespace = "com.hortencioflix.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hortencioflix.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.9.$runNumber"
    }

    signingConfigs {
        create("release") {
            // A chave vem do segredo do GitHub (o ficheiro .jks não está no repositório)
            storeFile = file("hortencioflix.jks")
            val pass = System.getenv("HF_KS_PASS")?.takeIf { it.isNotBlank() } ?: "hortencioflix"
            storePassword = pass
            keyAlias = "hortencioflix"
            keyPassword = pass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil:2.6.0")
}
