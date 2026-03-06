  rootProject.name = "result-root"

  include("result-core")
  include("result-springboot")

  // Optional: Use consistent plugin versions across the build
  pluginManagement {
    repositories {
      gradlePluginPortal()
      mavenCentral()
    }
  }

include("result-springboot")