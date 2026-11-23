plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional signing key for your own GitHub builds (kept in GitHub Secrets), so each APK installs OVER the last one.
// F-Droid never sees this: it builds the release unsigned and signs it with its own key.
val keystorePath: String? = System.getenv("UNWIND_KEYSTORE_PATH")

android {
    namespace = "com.unwind.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.unwind.launcher"
        minSdk = 24
        targetSdk = 34
        // Plain numbers on purpose: F-Droid reads them straight from this file.
        // Bump both for every release, and tag the commit v<versionName> (for example v2.5.1).
        versionCode = 2501
        versionName = "2.5.1"
    }

    // Two versions of the app from one code base:
    //  github = updates itself from GitHub releases (downloads and installs the APK)
    //  fdroid = no installer inside; it only checks F-Droid and opens its page
    flavorDimensions += "dist"
    productFlavors {
        create("github") { dimension = "dist" }
        create("fdroid") { dimension = "dist" }
    }

    // F-Droid asks for this: no extra dependency-info block inside the APK
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("unwind") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("UNWIND_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UNWIND_KEY_ALIAS")
                keyPassword = System.getenv("UNWIND_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("unwind")?.let { signingConfig = it }
        }
        release {
            // The release build is much smoother than the debug build, so this is the APK to install.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed only when your key is available. Without it the APK stays unsigned, which is what F-Droid wants.
            signingConfigs.findByName("unwind")?.let { signingConfig = it }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    // CHANGELOG.md stays at the top of the repo and is copied into the app at build time (it feeds What's New)
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/changelog"))
}

val copyChangelog = tasks.register<Copy>("copyChangelog") {
    from(rootProject.file("CHANGELOG.md"))
    rename { "changelog.md" }
    into(layout.buildDirectory.dir("generated/changelog"))
}
tasks.named("preBuild") { dependsOn(copyChangelog) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // Lets Android pre-compile the Compose code on first run, which removes most start-up stutter
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
