plugins {
  alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
  imports {
    mavenBom(libs.spring.boot.bom.get().toString())
  }
}

dependencies {
  api(project(":result-core"))

  compileOnly("org.springframework.boot:spring-boot-starter")
  compileOnly("org.springframework.boot:spring-boot-starter-web")

  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
}
