plugins {
  `java-library`
}

val examples by sourceSets.creating {
  java.srcDir("src/examples/java")

  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += output + compileClasspath
}

configurations[examples.implementationConfigurationName].extendsFrom(
  configurations.implementation.get()
)

dependencies {
  add(examples.implementationConfigurationName, rootProject.libs.jmh.core)
  add(examples.annotationProcessorConfigurationName, rootProject.libs.jmh.generator.annprocess)
}

tasks.register<JavaExec>("examples") {
  group = "examples"
  description = "Runs the Result examples"

  classpath = examples.runtimeClasspath
  mainClass = "io.github.joseevb.result.examples.ResultExamples"

  args(
    providers.gradleProperty("exampleArgs")
      .orElse("list")
      .map { it.split(" ") }
      .get()
  )
}

tasks.register<JavaExec>("benchmark") {
  group = "benchmark"
  description = "Benchmarks vanilla Java, Result, and optimized Java implementations"

  classpath = examples.runtimeClasspath
  mainClass = "io.github.joseevb.result.examples.ResultBenchmark"

  args(
    providers.gradleProperty("benchmarkArgs")
      .orElse("")
      .map { arguments -> arguments.split(" ").filter(String::isNotBlank) }
      .get()
  )
}
