plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "io.rikka.agent.model"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit4)
  testImplementation(libs.kotlinx.coroutines.test)
}
