import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  `java-library`
  jacoco
  id("com.diffplug.spotless") version "8.3.0" apply false
	id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

repositories {
  mavenCentral()
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "jacoco")
  apply(plugin = "com.diffplug.spotless")
	apply(plugin = "com.vanniktech.maven.publish")

	group = "io.github.joseevb"
  version = "0.1.0"

  repositories {
    mavenCentral()
  }

  tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--release", "25"))
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
          minimum = "0.9".toBigDecimal()
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
	configure<MavenPublishBaseExtension> {
		publishToMavenCentral()
		signAllPublications()

		pom {
			name.set(project.name)
			description.set("Result type library for Java")
			url.set("https://github.com/Joseevb/Result")

			licenses {
				license {
					name.set("MIT License")
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
				url.set("https://github.com/Joseevb/Result")
				connection.set("scm:git:https://github.com/Joseevb/Result.git")
				developerConnection.set("scm:git:ssh://git@github.com/Joseevb/Result.git")
			}
		}
	}

	// Spotless configuration
  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
      target("src/**/*.java")
      googleJavaFormat()
    }
  }

  tasks.named("check") {
    dependsOn("spotlessCheck", "jacocoTestCoverageVerification")
  }
}
