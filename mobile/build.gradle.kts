plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.fedorahosted.freeotp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.fedorahosted.freeotp"
        minSdk = 26
        targetSdk = 35
        versionCode = 48
        versionName = "2.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFiles("test-proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // `./gradlew connectedAndroidTest -PtestMinified` runs the instrumented tests against the
    // R8-minified release build (signed with the debug key) to verify the keep rules, notably
    // BackupFormatGoldenTest. Compose UI tests (package `org.fedorahosted.freeotp.ui`) are skipped:
    // they need desugared library classes that R8 renames in the app APK.
    if (providers.gradleProperty("testMinified").isPresent) {
        testBuildType = "release"
        defaultConfig.testInstrumentationRunnerArguments["notPackage"] = "org.fedorahosted.freeotp.ui"
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            proguardFile("proguard-rules-minified-tests.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.commons.codec)
    implementation(libs.gson)
    implementation(libs.zxing.core)
    implementation(libs.nbvcxz)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
