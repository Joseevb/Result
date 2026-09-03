plugins {
  `java-library`
  `maven-publish`
  signing
  jacoco
  id("com.diffplug.spotless") version "8.3.0" apply false
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")
  apply(plugin = "signing")
  apply(plugin = "jacoco")
  apply(plugin = "com.diffplug.spotless")

	group = "io.github.joseevb"
  version = "0.1.0"

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
          url.set("https://github.com/Joseevb/Result")

          licenses {
            license {
              name.set("MIT")
              url.set("https://opensource.org/licenses/MIT")
            }
          }

          developers {
            developer {
              id.set("joseevb")
              name.set("Jose Vasquez")
              email.set("joseevb@protonmail.com")
            }
          }

          scm {
            connection.set("scm:git:git://github.com/Joseevb/Result")
            developerConnection.set("scm:git:ssh://github.com/Joseevb/Result")
            url.set("https://github.com/Joseevb/Result")
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

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
      target("src/**/*.java")
      googleJavaFormat()
    }
  }

  tasks.named("check") {
    dependsOn("spotlessCheck")
  }
}
