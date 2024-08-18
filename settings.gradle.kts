@file:Suppress("UnstableApiUsage")

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

include(":quickie")
include(":quickie-foss")
include(":sample")