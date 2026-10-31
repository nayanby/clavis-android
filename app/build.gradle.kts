import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
providers.gradleProperty("resonBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }
val uploadSigningFile = rootProject.file("upload-signing.properties")
val uploadSigning = Properties().apply { if (uploadSigningFile.exists()) uploadSigningFile.inputStream().use { load(it) } }
android {
    signingConfigs {
        if (uploadSigningFile.exists()) create("upload") {
            storeFile = rootProject.file(uploadSigning.getProperty("storeFile"))
            storePassword = uploadSigning.getProperty("storePassword")
            keyAlias = uploadSigning.getProperty("keyAlias")
            keyPassword = uploadSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            if (uploadSigningFile.exists()) signingConfig = signingConfigs.getByName("upload")
        }
    }
    namespace = "com.clavis.reader"
    compileSdk = 36
    kotlinOptions { jvmTarget = "17" }
    defaultConfig { applicationId = "com.clavis.reader"; testInstrumentationRunner = providers.gradleProperty("clavisTestRunner").getOrElse("com.clavis.reader.ReaderChecks"); minSdk = 26; targetSdk = 36; versionCode = 91; versionName = "0.10.13" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(files("libs/jsoup-1.18.3.jar"))
    implementation("com.artifex.mupdf:fitz:1.28.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.customview:customview:1.1.0")
    implementation("androidx.collection:collection:1.1.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
    implementation(files("libs/libtorrent4j-2.1.0-39.jar"))
    implementation(files("libs/vosk-android-0.3.75.aar", "libs/jna-5.18.1.aar"))
}
