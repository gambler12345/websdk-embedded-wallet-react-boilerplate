plugins { id("com.android.application") }

android {
    namespace = "de.mpconsulting.autoclickpoint"
    compileSdk = 36
    defaultConfig {
        applicationId = "de.mpconsulting.autoclickpoint"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.7"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
