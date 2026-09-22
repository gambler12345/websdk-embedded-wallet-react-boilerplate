plugins { id("com.android.application") }

android {
    namespace = "de.mpconsulting.autoclickpoint"
    compileSdk = 36
    defaultConfig {
        applicationId = "de.mpconsulting.autoclickpoint"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
