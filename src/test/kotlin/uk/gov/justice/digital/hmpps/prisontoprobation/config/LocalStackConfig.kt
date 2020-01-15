package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.localstack.LocalStackContainer

@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "embedded-localstack")
@Profile("test-queue")
open class LocalStackConfig {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }


  @Bean
  open fun localStackContainer(): LocalStackContainer {
    log.info("Starting localstack...")
    val localStackContainer: LocalStackContainer = LocalStackContainer()
            .withServices(LocalStackContainer.Service.SQS)
            .withEnv("HOSTNAME_EXTERNAL", "localhost")

    localStackContainer.start()
    log.info("Started localstack.")
    return localStackContainer
  }

}
