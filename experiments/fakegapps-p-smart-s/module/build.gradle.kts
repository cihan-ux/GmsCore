plugins { id("com.android.application") }
android {
    namespace = "inc.whew.android.fakegapps.psmarts"
    compileSdk = 35
    defaultConfig {
        applicationId = "inc.whew.android.fakegapps.psmarts"
        minSdk = 29
        targetSdk = 29
        versionCode = 1
        versionName = "0.1-psmarts-experimental"
    }
}
dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
