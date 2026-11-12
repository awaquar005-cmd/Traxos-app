plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val smsPolicyApproved=providers.gradleProperty("traxosSmsPolicyApproved").map {it=="true"}.getOrElse(false)
// Enable only after documented Google Play exception approval; runtime consent is still required.
android {
    namespace = "app.traxos.finance"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "app.traxos.finance"
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetSdk = 36
        buildConfigField("boolean","SMS_POLICY_APPROVED",smsPolicyApproved.toString())
        versionCode = 6
        versionName = "0.5.0-smart-security"
    }
    if(smsPolicyApproved) sourceSets.getByName("debug").manifest.srcFile("src/smsApproved/AndroidManifest.xml")
    sourceSets.getByName("test").resources.srcDir("schemas")
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "512m"
            it.maxParallelForks = 1
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.savedstate)
    implementation(libs.navigation)
    implementation(libs.coroutines)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.paging)
    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.fragment)
    implementation(libs.appcompat)
    implementation(libs.exif)
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.barcode)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.ext)
    androidTestImplementation(libs.coroutines)
    testImplementation(libs.work.testing)
    testImplementation(libs.compose.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.android.test.core)
}
