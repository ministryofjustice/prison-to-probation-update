plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "0.3.0"
  kotlin("plugin.spring") version "1.3.72"
}

extra["spring-security.version"] = "5.3.2.RELEASE" // Updated since spring-boot-starter-oauth2-resource-server-2.2.7.RELEASE only pulls in 5.2.4.RELEASE (still affected by CVE-2018-1258 though)

configurations {
  implementation { exclude(group = "tomcat-jdbc") }
}

ext ["spring-security.version"] = "5.3.2.RELEASE" // pinned due to CVE-2018-1258 in 5.2.2.RELEASE brought in by spring-boot-starter-oauth2-resource-server-2.2.5.RELEASE

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  
  implementation("net.javacrumbs.shedlock:shedlock-spring:4.9.3")
  implementation("net.javacrumbs.shedlock:shedlock-provider-dynamodb:4.9.3")

  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.sun.xml.bind:jaxb-impl:2.3.3")
  implementation("com.sun.xml.bind:jaxb-core:2.3.0.1")
  implementation("com.google.code.gson:gson:2.8.6")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.0")

  implementation("org.springframework:spring-jms")
  implementation(platform("com.amazonaws:aws-java-sdk-bom:1.11.782"))
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
  implementation("com.amazonaws:aws-java-sdk-dynamodb:1.11.782")
  implementation("io.github.boostchicken:spring-data-dynamodb:5.2.3")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.26.3")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.17.0")
  testImplementation("com.nhaarman:mockito-kotlin-kt1.1:1.6.0")
  testImplementation("org.testcontainers:localstack:1.13.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.0.2")
}
