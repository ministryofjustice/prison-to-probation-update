package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.localstack.LocalStackContainer

@Configuration
@Profile("integration-message-test")
open class LocalStackConfig {
  private val localStackContainer: LocalStackContainer = LocalStackContainer()
          .withServices(LocalStackContainer.Service.SQS)
          .withEnv("HOSTNAME_EXTERNAL", "localhost")

  @Bean
  open fun localStackContainer(): LocalStackContainer {
    localStackContainer.start()
    return localStackContainer
  }

}
