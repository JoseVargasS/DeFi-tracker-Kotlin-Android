plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.defitracker.app"
    //noinspection OldTargetApi,ExpiredTargetSdkVersion
    compileSdk = 34

    defaultConfig {
        applicationId = "com.defitracker.app"
        minSdk = 26
        //noinspection OldTargetApi,ExpiredTargetSdkVersion
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        disable += setOf(
            "GradleDependency",
            "IconDuplicates",
            "IconLauncherShape",
            "IconLocation",
            "MonochromeLauncherIcon",
            "OldTargetApi",
            "SmallSp"
        )
    }
}

dependencies {
    //noinspection GradleDependency
    implementation("androidx.core:core-ktx:1.12.0")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    //noinspection GradleDependency
    implementation("androidx.activity:activity-compose:1.8.2")
    //noinspection GradleDependency
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    //noinspection GradleDependency
    implementation("androidx.navigation:navigation-compose:2.7.6")
    //noinspection GradleDependency
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Material Components
    //noinspection GradleDependency
    implementation("com.google.android.material:material:1.11.0")
    
    // Retrofit
    //noinspection GradleDependency
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    //noinspection GradleDependency
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // Hilt
    //noinspection GradleDependency
    implementation("com.google.dagger:hilt-android:2.50")
    //noinspection GradleDependency
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    
    // Room
    //noinspection GradleDependency
    implementation("androidx.room:room-runtime:2.6.1")
    //noinspection GradleDependency
    implementation("androidx.room:room-ktx:2.6.1")
    //noinspection GradleDependency
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Glance (Widgets)
    //noinspection GradleDependency
    implementation("androidx.glance:glance-appwidget:1.0.0")
    
    // Coil (Images)
    //noinspection GradleDependency
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Splash Screen
    //noinspection GradleDependency
    implementation("androidx.core:core-splashscreen:1.0.1")
    // DataStore (indicadores persistentes)
    //noinspection GradleDependency
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
