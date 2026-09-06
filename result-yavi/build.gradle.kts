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
  api(project(":result-core"))
  api(rootProject.libs.yavi)

  add(examples.implementationConfigurationName, rootProject.libs.jmh.core)
  add(examples.annotationProcessorConfigurationName, rootProject.libs.jmh.generator.annprocess)
}

tasks.register<JavaExec>("examples") {
  group = "examples"
  description = "Runs the result-yavi examples"

  classpath = examples.runtimeClasspath
  mainClass = "io.github.joseevb.result.yavi.examples.ValidatedResultExamples"
}

tasks.register<JavaExec>("benchmark") {
  group = "benchmark"
  description = "Benchmarks direct YAVI validation against the Result adapter"

  classpath = examples.runtimeClasspath
  mainClass = "io.github.joseevb.result.yavi.examples.ValidatedResultBenchmark"

  args(
    providers.gradleProperty("benchmarkArgs")
      .orElse("")
      .map { arguments -> arguments.split(" ").filter(String::isNotBlank) }
      .get()
  )
}
