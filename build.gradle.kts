plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.0.0"
  kotlin("plugin.spring") version "1.4.21"
  id("org.unbroken-dome.test-sets") version "3.0.1"
  idea
}

configurations {
  implementation { exclude(group = "tomcat-jdbc") }
}

testSets {
  "testIntegration"()
  "testSmoke"()
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("net.javacrumbs.shedlock:shedlock-spring:4.20.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-dynamodb:4.20.0")

  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.google.code.gson:gson:2.8.6")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.1")

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.5.2")
  implementation("org.springdoc:springdoc-openapi-ui:1.5.2")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.2")

  implementation("org.springframework:spring-jms")
  implementation(platform("com.amazonaws:aws-java-sdk-bom:1.11.942"))
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
  implementation("com.amazonaws:aws-java-sdk-dynamodb")
  implementation("io.github.boostchicken:spring-data-dynamodb:5.2.5")

  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("io.micrometer:micrometer-core")
  implementation("com.opencsv:opencsv:5.3")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.22.1")
  testImplementation("org.testcontainers:localstack:1.15.1")
  testImplementation("org.awaitility:awaitility-kotlin:4.0.3")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.4.2")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
}
