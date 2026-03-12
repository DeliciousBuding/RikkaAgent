@file:Suppress("UnstableApiUsage")

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "io.rikka.agent"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "io.rikka.agent"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
  }

  flavorDimensions += "env"
  productFlavors {
    create("dev") {
      dimension = "env"
      applicationIdSuffix = ".dev"
      versionNameSuffix = "-dev"
      resValue("string", "app_name", "Rikka Agent (Dev)")
    }
    create("prod") {
      dimension = "env"
      resValue("string", "app_name", "Rikka Agent")
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

  buildFeatures { compose = true }


  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation(platform(libs.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.material3)
  implementation(libs.accompanist.systemuicontroller)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.serialization.json)

  implementation(projects.core.model)
  implementation(projects.core.ui)
  implementation(projects.core.ssh)
  implementation(projects.core.storage)
}
