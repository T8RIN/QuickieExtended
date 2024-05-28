plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.kotlin.dokka)
  `maven-publish`
}

afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("bundledRelease") { commonConfig("bundled") }
      create<MavenPublication>("unbundledRelease") { commonConfig("unbundled") }
    }
  }
}

android {
  namespace = "io.github.g00fy2.quickie"
  resourcePrefix = "quickie"
  buildFeatures {
    viewBinding = true
  }
  flavorDimensions += "mlkit"
  productFlavors {
    create("bundled").dimension = "mlkit"
    create("unbundled").dimension = "mlkit"
  }
  sourceSets {
    getByName("bundled").java.srcDirs("src/bundled/kotlin")
    getByName("unbundled").java.srcDirs("src/unbundled/kotlin")
  }
  publishing {
    singleVariant("bundledRelease")
    singleVariant("unbundledRelease")
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
}

group = "io.github.g00fy2.quickie"
version = libs.versions.quickie.get()


fun MavenPublication.commonConfig(flavor: String) {
  from(components["${flavor}Release"])
  artifactId = "quickie-$flavor"
  version = "1.10.0"
  groupId = "com.github.t8rin"
}