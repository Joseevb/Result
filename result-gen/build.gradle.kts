plugins {
  `java-library`
}

dependencies {
  api(project(":result-core"))
  implementation(libs.eclipse.jdt.core)
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
  }
}
