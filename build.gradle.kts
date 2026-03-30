plugins {
  `java-library`
  `maven-publish`
  signing
  jacoco
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")
  apply(plugin = "signing")
  apply(plugin = "jacoco")

  group = "dev.jose"
  version = "0.1.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }

  java {
    withSourcesJar()
    withJavadocJar()
  }

  tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "--release", "25"))
  }

  dependencies {
    // Nullability annotations
    compileOnly(rootProject.libs.jspecify)
    compileOnly(rootProject.libs.jetbrains.annotations)

    // Observability & Logging (compileOnly - users bring their own)
    compileOnly(rootProject.libs.micrometer.core)
    compileOnly(rootProject.libs.slf4j.api)

    // JUnit 6 - unified BOM for all JUnit components
    testImplementation(platform(rootProject.libs.junit.bom))
    testImplementation(rootProject.libs.junit.jupiter)
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
  }

  tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
  }

  tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
  }

  tasks.jacocoTestCoverageVerification {
    violationRules {
      rule {
        limit {
          minimum = "0.8".toBigDecimal()
        }
      }

      rule {
        isEnabled = false
        element = "CLASS"
        includes = listOf("org.gradle.*")

        limit {
          counter = "LINE"
          value = "TOTALCOUNT"
          maximum = "0.8".toBigDecimal()
        }
      }
    }
  }

  // Publishing configuration
  publishing {
    publications {
      create<MavenPublication>("maven") {
        from(components["java"])

        pom {
          name.set(project.name)
          description.set("Result type library for Java")
          url.set("https://github.com/jose/result")

          licenses {
            license {
              name.set("MIT")
              url.set("https://opensource.org/licenses/MIT")
            }
          }

          developers {
            developer {
              id.set("jose")
              name.set("Jose")
              email.set("jose@example.com")
            }
          }

          scm {
            connection.set("scm:git:git://github.com/jose/result.git")
            developerConnection.set("scm:git:ssh://github.com:jose/result.git")
            url.set("https://github.com/jose/result")
          }
        }
      }
    }
  }

  // Only sign when not publishing to local Maven
  signing {
    setRequired {
      !project.version.toString().endsWith("SNAPSHOT") &&
          !gradle.taskGraph.hasTask(":${project.name}:publishToMavenLocal")
    }
    sign(publishing.publications["maven"])
  }
}
