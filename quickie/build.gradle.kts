plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.kotlin.dokka)
  `maven-publish`
  signing
}

android {
  namespace = "io.github.g00fy2.quickie"
  resourcePrefix = "quickie"
  compileSdk = 35
  buildFeatures {
    viewBinding = true
  }

  defaultConfig {
    minSdk = 21
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
  implementation(libs.coil)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core)

  implementation(libs.androidx.camera)
  implementation(libs.androidx.cameraLifecycle)
  implementation(libs.androidx.cameraPreview)

  add("bundledImplementation", libs.mlkit.barcodeScanning)
  add("unbundledImplementation", libs.mlkit.barcodeScanningGms)

  testImplementation(libs.test.junitApi)
  testRuntimeOnly(libs.test.junitEngine)

  coreLibraryDesugaring(libs.desugaring)
}

group = "io.github.g00fy2.quickie"
version = libs.versions.quickie.get()


afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("bundledRelease") { commonConfig("bundled") }
      create<MavenPublication>("unbundledRelease") { commonConfig("unbundled") }
    }
    repositories {
      maven {
        name = "sonatype"
        url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        credentials {
          username = findStringProperty("sonatypeUsername")
          password = findStringProperty("sonatypePassword")
        }
      }
    }
  }
}

fun MavenPublication.commonConfig(flavor: String) {
  from(components["${flavor}Release"])
  artifactId = "quickie-$flavor"
  pom {
    name = "quickie-$flavor"
    description = "Android QR code scanning library"
    url = "https://github.com/T8RIN/QuickieExtended"
    licenses {
      license {
        name = "MIT License"
        url = "https://opensource.org/licenses/MIT"
      }
    }
    developers {
      developer {
        id = "g00fy2"
        name = "Thomas Wirth"
        email = "twirth.development@gmail.com"
      }
    }
    scm {
      connection = "https://github.com/T8RIN/QuickieExtended.git"
      developerConnection = "https://github.com/T8RIN/QuickieExtended.git"
      url = "https://github.com/T8RIN/QuickieExtended"
    }
  }
}

fun Project.findStringProperty(propertyName: String): String? {
  return findProperty(propertyName) as String? ?: run {
    println("$propertyName missing in gradle.properties")
    null
  }
}