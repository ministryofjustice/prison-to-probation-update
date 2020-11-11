plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "1.0.6"
  kotlin("plugin.spring") version "1.4.10"
}

configurations {
  implementation { exclude(group = "tomcat-jdbc") }
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  
  implementation("net.javacrumbs.shedlock:shedlock-spring:4.14.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-dynamodb:4.14.0")

  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.sun.xml.bind:jaxb-impl:2.3.3")
  implementation("com.sun.xml.bind:jaxb-core:2.3.0.1")
  implementation("com.google.code.gson:gson:2.8.6")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.2")

  implementation("org.springframework:spring-jms")
  implementation(platform("com.amazonaws:aws-java-sdk-bom:1.11.873"))
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
  implementation("com.amazonaws:aws-java-sdk-dynamodb:1.11.873")
  implementation("com.github.derjust:spring-data-dynamodb:5.1.0")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.19.0")
  testImplementation("com.nhaarman:mockito-kotlin-kt1.1:1.6.0")
  testImplementation("org.testcontainers:localstack:1.14.3")
  testImplementation("org.awaitility:awaitility-kotlin:4.0.3")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.4.1")
}

tasks.withType<Test> {
  if (System.getProperty("test.profile") == "unit") {
    exclude("**/*MessageIntegrationTest*")
    exclude("**/smoketest/**")
  }
  if (System.getProperty("test.profile") == "integration") {
    include("**/*MessageIntegrationTest*")
    exclude("**/smoketest/**")
  }
  if (System.getProperty("test.profile") == "smoke") {
    include("**/smoketest/**")
  }
}
if (System.getProperty("test.profile") == "integration") {
  reporting.baseDir = File("$buildDir/reports/tests/integration")
  project.setProperty("testResultsDirName", "$buildDir/test-results/integration")
}

if (System.getProperty("test.profile") == "unit") {
  reporting.baseDir = File("$buildDir/reports/tests/unit")
  project.setProperty("testResultsDirName", "$buildDir/test-results/unit")
}

if (System.getProperty("test.profile") == "smoke") {
  reporting.baseDir = File("$buildDir/reports/tests/smoke")
  project.setProperty("testResultsDirName", "$buildDir/test-results/smoke")
}

testlogger {
  if (System.getProperty("test.profile") == "smoke") {
      showStandardStreams = true
  }
}