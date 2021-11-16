plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.3.13"
  kotlin("plugin.spring") version "1.5.31"
  id("org.unbroken-dome.test-sets") version "4.0.0"
  idea
}

testSets {
  "testIntegration"()
  "testSmoke"()
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

  implementation("net.javacrumbs.shedlock:shedlock-spring:4.28.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-dynamodb:4.28.0")

  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.google.code.gson:gson:2.8.8")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.0")

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.5.10")
  implementation("org.springdoc:springdoc-openapi-ui:1.5.10")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.10")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.0.2")
  implementation("com.amazonaws:aws-java-sdk-dynamodb")
  implementation("io.github.boostchicken:spring-data-dynamodb:5.2.5")

  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("io.micrometer:micrometer-core")
  implementation("com.opencsv:opencsv:5.5.2")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.28.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.1.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.5.2")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.mockito:mockito-inline:3.12.4")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(16))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
  }
}
