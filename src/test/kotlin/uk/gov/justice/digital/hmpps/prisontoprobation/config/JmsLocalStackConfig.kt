package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.localstack.LocalStackContainer


@Configuration
@Profile("integration-message-test")
open class JmsLocalStackConfig(private val localStackContainer: LocalStackContainer) {

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "test-localstack")
  open fun awsLocalTestClient(): AmazonSQS {
    return AmazonSQSClientBuilder.standard()
            .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
            .build()
  }
}
