plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The version code must go up with every build that installs over an older one,
// so it's the number of commits. Release builds from CI set the name from the git tag.
val commitCount: Int = try {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
} catch (e: Exception) {
    1
}

// Set by scripts/signing.sh and the release workflow. Without them, release builds
// use the debug key, so anyone can build and install their own copy.
val releaseKeystore: String? = providers.environmentVariable("I5_KEYSTORE").orNull

android {
    namespace = "com.andrewmacheret.i5"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.andrewmacheret.i5"
        minSdk = 26
        targetSdk = 37
        versionCode = commitCount
        versionName = providers.gradleProperty("i5.versionName").getOrElse("dev-$commitCount")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("I5_KEYSTORE_PASSWORD").get()
                keyAlias = "i5"
                keyPassword = providers.environmentVariable("I5_KEYSTORE_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
