import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.reaido.unireader"
    compileSdk = 36

    val versionPropsFile = rootProject.file("version.properties")
    val versionProps = Properties()
    if (versionPropsFile.exists()) {
        versionProps.load(FileInputStream(versionPropsFile))
    } else {
        versionProps["VERSION_CODE"] = "1"
    }
    val currentVersionCode = versionProps["VERSION_CODE"].toString().toInt()

    defaultConfig {
        applicationId = "com.reaido.unireader"
        minSdk = 26
        targetSdk = 36
        versionCode = currentVersionCode
        versionName = "1.0.$currentVersionCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { file(it as String) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

tasks.register("incrementVersionCode") {
    doLast {
        val versionPropsFile = rootProject.file("version.properties")
        val versionProps = Properties()
        if (versionPropsFile.exists()) {
            versionProps.load(FileInputStream(versionPropsFile))
            val code = versionProps["VERSION_CODE"].toString().toInt()
            versionProps["VERSION_CODE"] = (code + 1).toString()
            versionProps.store(FileOutputStream(versionPropsFile), null)
            println("VersionCode incremented to ${code + 1}")
        }
    }
}

// Автоматически запускать инкремент при сборке Bundle или Release APK
tasks.whenTaskAdded {
    if (name == "generateReleaseBuildConfig" || name == "bundleRelease") {
        dependsOn("incrementVersionCode")
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}