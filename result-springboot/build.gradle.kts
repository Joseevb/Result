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
  compileOnly("io.micrometer:micrometer-core")

  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-web")
  testImplementation("jakarta.servlet:jakarta.servlet-api")
  testImplementation("io.micrometer:micrometer-core")
}
