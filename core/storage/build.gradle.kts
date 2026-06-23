plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
  namespace = "io.rikka.agent.storage"
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
  implementation(projects.core.model)
  implementation(libs.kotlinx.serialization.json)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  // DataStore
  implementation(libs.datastore.preferences)

  // Unit tests
  testImplementation(libs.junit4)

  // Android instrumented tests (Room migration testing)
  androidTestImplementation(libs.junit4)
  androidTestImplementation(libs.room.testing)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.runner)
}
