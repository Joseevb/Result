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
  compileOnly("org.springframework:spring-aop")
  compileOnly("org.springframework:spring-tx")

  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
  testImplementation("org.springframework.boot:spring-boot-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-web")
  testImplementation("com.h2database:h2")
  testImplementation("jakarta.servlet:jakarta.servlet-api")
}
