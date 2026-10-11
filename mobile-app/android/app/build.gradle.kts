import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.garbageguard.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.garbageguard.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "6.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The team's tested Firebase set, read from local.properties (git-ignored) so the
        // password never reaches the public repository. Missing values become empty.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
        fun prop(key: String) = "\"" + (local.getProperty(key) ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        buildConfigField("String", "FB_URL", prop("gg.fb.url"))
        buildConfigField("String", "FB_KEY", prop("gg.fb.key"))
        buildConfigField("String", "FB_EMAIL", prop("gg.fb.email"))
        buildConfigField("String", "FB_PASSWORD", prop("gg.fb.password"))
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

}