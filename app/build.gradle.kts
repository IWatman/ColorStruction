import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Version comes from one place; `-PappVersion=0.0.9` overrides it (used to test updates).
val appVersion: String = (findProperty("appVersion") as String?) ?: "0.3.0"

/** 0.1.0 -> 100, 1.2.3 -> 10203: newer versions always get a larger versionCode. */
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.split(".").map { it.takeWhile(Char::isDigit).toInt() }
    return major * 10_000 + minor * 100 + patch
}

// Release signing: keystore.properties outside the repo (path overridable via env, e.g. in CI).
val keystoreProperties = Properties().apply {
    val path = System.getenv("COLORSTRUCTION_KEYSTORE_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.colorstruction/keystore.properties"
    val file = File(path)
    if (file.isFile) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.colorstruction"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.colorstruction"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric and sqlite-jdbc unpack native libs into java.io.tmpdir; keep them inside
        // the build directory so the project root stays clean.
        unitTests.all { test ->
            val tempDir = layout.buildDirectory.dir("tmp/unitTests").get().asFile
            tempDir.mkdirs()
            test.systemProperty("java.io.tmpdir", tempDir.absolutePath)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
