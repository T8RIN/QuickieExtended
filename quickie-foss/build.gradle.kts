plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.kotlin.dokka)
  id("maven-publish")
}

afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("mavenJava") {
        from(components["release"])
        version = "1.12.0"
        groupId = "com.github.t8rin"
        artifactId = "quickie-foss"
      }
    }
  }
}

android {
  namespace = "io.github.g00fy2.quickie"
  resourcePrefix = "quickie"
  compileSdk = 34
  buildFeatures {
    viewBinding = true
  }

  defaultConfig {
    minSdk = 21
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.toString()
  }

}

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core)

  implementation(libs.androidx.camera)
  implementation(libs.androidx.cameraLifecycle)
  implementation(libs.androidx.cameraPreview)
  implementation(libs.zxing.android.embedded)

  testImplementation(libs.test.junitApi)
  testRuntimeOnly(libs.test.junitEngine)
  coreLibraryDesugaring(libs.desugaring)
}

group = "io.github.g00fy2.quickie"
version = libs.versions.quickie.get()