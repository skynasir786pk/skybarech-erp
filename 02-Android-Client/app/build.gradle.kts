plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.skybarech.mobileshoperp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skybarech.mobileshoperp"
        minSdk = 26
        targetSdk = 35
        versionCode = 29
        versionName = "1.3.29"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${providers.gradleProperty("SKYBARECH_API_BASE_URL").orNull.orEmpty().trimEnd('/')}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("production") {
            val keyPath = System.getenv("SKYBARECH_KEYSTORE_PATH")
            if (!keyPath.isNullOrBlank()) storeFile = file(keyPath)
            storePassword = System.getenv("SKYBARECH_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SKYBARECH_KEY_ALIAS")
            keyPassword = System.getenv("SKYBARECH_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime-saveable")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val validateProduction by tasks.registering {
    doLast {
        val base = providers.gradleProperty("SKYBARECH_API_BASE_URL").orNull.orEmpty()
        require(base.startsWith("https://") && !base.contains("YOUR-DOMAIN")) { "Set the production HTTPS API URL." }
        listOf("SKYBARECH_KEYSTORE_PATH", "SKYBARECH_KEYSTORE_PASSWORD", "SKYBARECH_KEY_ALIAS", "SKYBARECH_KEY_PASSWORD").forEach {
            require(!System.getenv(it).isNullOrBlank()) { "Missing release signing environment variable: $it" }
        }
        require(file(System.getenv("SKYBARECH_KEYSTORE_PATH")).isFile) { "Release keystore does not exist." }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateProduction)
}
