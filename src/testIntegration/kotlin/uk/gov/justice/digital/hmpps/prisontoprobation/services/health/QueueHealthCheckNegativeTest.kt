package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.config.SqsConfigProperties
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueHealth

@Import(QueueHealthCheckNegativeTest.TestConfig::class)
class QueueHealthCheckNegativeTest : NoQueueListenerIntegrationTest() {

  @TestConfiguration
  class TestConfig {
    @Bean
    fun badQueueHealth(sqsConfigProperties: SqsConfigProperties): HmppsQueueHealth {
      val sqsClient = AmazonSQSClientBuilder.standard()
        .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(sqsConfigProperties.localstackUrl, sqsConfigProperties.region))
        .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
        .build()
      return HmppsQueueHealth(HmppsQueue("missingQueueId", sqsClient, "missingQueue", sqsClient, "missingDlq"))
    }
  }

  @Test
  fun `Queue health down`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.badQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("components.badQueueHealth.details.queueName").isEqualTo("missingQueue")
      .jsonPath("components.badQueueHealth.details.dlqName").isEqualTo("missingDlq")
      .jsonPath("components.badQueueHealth.details.error").exists()
  }
}
