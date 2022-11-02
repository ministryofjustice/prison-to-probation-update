plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.5.7"
  kotlin("plugin.spring") version "1.7.20"
  id("org.unbroken-dome.test-sets") version "4.0.0"
  id("com.google.cloud.tools.jib") version "3.3.0"
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

  implementation("net.javacrumbs.shedlock:shedlock-spring:4.42.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-dynamodb:4.42.0")

  implementation("javax.transaction:javax.transaction-api")
  implementation("javax.xml.bind:jaxb-api")
  implementation("com.google.code.gson:gson")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.6.12")
  implementation("org.springdoc:springdoc-openapi-ui:1.6.12")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.12")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.12")
  implementation("com.amazonaws:aws-java-sdk-dynamodb")
  implementation("io.github.boostchicken:spring-data-dynamodb:5.2.5")

  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("io.micrometer:micrometer-core")
  implementation("com.opencsv:opencsv:5.7.0")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.36.0")
  testImplementation("org.awaitility:awaitility-kotlin")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.mockito:mockito-inline")
  testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {

  val copyAgentJar by registering(Copy::class) {
    from("${project.buildDir}/libs")
    include("applicationinsights-agent*.jar")
    into("${project.buildDir}/agent")
    rename("applicationinsights-agent(.+).jar", "agent.jar")
    dependsOn("assemble")
  }

  val jib by getting {
    dependsOn += copyAgentJar
  }

  val jibBuildTar by getting {
    dependsOn += copyAgentJar
  }

  val jibDockerBuild by getting {
    dependsOn += copyAgentJar
  }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }
}

jib {
  container {
    creationTime = "USE_CURRENT_TIMESTAMP"
    jvmFlags = mutableListOf("-Duser.timezone=Europe/London")
    mainClass = "uk.gov.justice.digital.hmpps.prisontoprobation.PrisonToProbationUpdateApplicationKt"
    user = "2000:2000"
  }
  from {
    image = "ghcr.io/ministryofjustice/hmpps-probation-integration-services/eclipse-temurin:17-jre-alpine"
  }
  extraDirectories {
    paths {
      path {
        setFrom("${project.buildDir}")
        includes.add("agent/agent.jar")
      }
      path {
        setFrom("${project.rootDir}")
        includes.add("applicationinsights*.json")
        into = "/agent"
      }
    }
  }
}
