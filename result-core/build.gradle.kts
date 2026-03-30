plugins {
  `java-library`
}

dependencies {
  testImplementation(rootProject.libs.micrometer.core)
  testImplementation(rootProject.libs.slf4j.api)
}
