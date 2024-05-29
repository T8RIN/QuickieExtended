include(":quickie")

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
}

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
include(":app")
