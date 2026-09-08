import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val oneLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

val oneSigningProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun onePublicValue(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orNull
        ?: oneLocalProperties.getProperty(name)
        ?: fallback

android {
    namespace = "com.oneglobal.billboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tomribowei.one"
        minSdk = 26
        targetSdk = 36
        versionCode = 28
        versionName = "1.0.18"
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"" + onePublicValue("ADMOB_REWARDED_UNIT_ID") + "\"")
        manifestPlaceholders["admobAppId"] = onePublicValue("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        val revenueCatKey = onePublicValue("REVENUECAT_API_KEY")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"" + onePublicValue("GOOGLE_WEB_CLIENT_ID") + "\"")
        val oneSignalId = onePublicValue("ONESIGNAL_APP_ID")
        val supabaseUrl = onePublicValue("SUPABASE_URL")
        val supabaseAnonKey = onePublicValue("SUPABASE_ANON_KEY")
        val oneWebUrl = onePublicValue("ONE_WEB_URL", "https://one-global-screen.no1tomcodes.chatgpt.site")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatKey\"")
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"$oneSignalId\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "ONE_WEB_URL", "\"$oneWebUrl\"")
    }

    signingConfigs {
        create("release") {
            if (oneSigningProperties.isNotEmpty()) {
                storeFile = rootProject.file(oneSigningProperties.getProperty("storeFile"))
                storePassword = oneSigningProperties.getProperty("storePassword")
                keyAlias = oneSigningProperties.getProperty("keyAlias")
                keyPassword = oneSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            val url = onePublicValue("SUPABASE_URL")
            val key = onePublicValue("SUPABASE_ANON_KEY")
            if (!url.startsWith("https://") || key.isBlank()) {
                throw GradleException("Release builds require a live SUPABASE_URL and SUPABASE_ANON_KEY.")
            }
            if (oneSigningProperties.isEmpty()) {
                throw GradleException("Release builds require keystore.properties.")
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.foundation:foundation:1.7.6")
    implementation("androidx.compose.animation:animation:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")

    implementation("com.revenuecat.purchases:purchases:10.15.1")
    implementation("com.revenuecat.purchases:purchases-admob:10.15.1")
    implementation("com.google.android.gms:play-services-ads:24.0.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation("com.onesignal:OneSignal:5.9.8")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.50")

    testImplementation("junit:junit:4.13.2")
}
