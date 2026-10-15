plugins {
  application
}

dependencies {
  implementation(project(":result-core"))
  implementation(project(":result-gen"))
}

val generatedSources = layout.buildDirectory.dir("generated/sources/resultGen/main")
val inputSource = layout.projectDirectory.file("src/main/java/demo/Main.java")

sourceSets {
  main {
    java.setSrcDirs(listOf(generatedSources))
  }
}

val transformResultGen by tasks.registering(JavaExec::class) {
  group = "build"
  description = "Transforms the ResultGen demo source before javac compiles it."

  dependsOn(":result-core:classes", ":result-gen:classes")
  // Dependency runtime classpath only: sourceSets.main.runtimeClasspath also includes
  // this project's output, which depends on compileJava and would create a task cycle.
  classpath = configurations.runtimeClasspath.get()
  mainClass.set("io.github.joseevb.result.gen.ResultGenTransformer")

  val output = generatedSources.map { it.file("demo/Main.java") }
  inputs.file(inputSource)
  outputs.file(output)

  // Keep task arguments as plain values rather than resolving sourceSets inside a
  // doFirst closure, which captures the Gradle script and breaks configuration-cache storage.
  val compileClasspath = sourceSets.main.get().compileClasspath
  args(
    inputSource.asFile.absolutePath,
    output.get().asFile.absolutePath
  )
  args(compileClasspath.files.map { it.absolutePath })
}

tasks.named<JavaCompile>("compileJava") {
  dependsOn(transformResultGen)
}

application {
  mainClass.set("demo.Main")
}

tasks.register<JavaExec>("runExample") {
  group = "application"
  description = "Builds the transformed demo and runs its success/failure scenarios."
  dependsOn(tasks.named("classes"))
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("demo.Main")
}
