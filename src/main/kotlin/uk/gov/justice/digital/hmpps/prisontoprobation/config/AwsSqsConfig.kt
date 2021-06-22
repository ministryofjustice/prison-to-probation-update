package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Configuration
@ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "aws")
class AwsSqsConfig(private val hmppsQueueService: HmppsQueueService) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  fun awsSqsClient(sqsConfigProperties: SqsConfigProperties, awsSqsDlqClient: AmazonSQS): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(prisonEventQueue().queueAccessKeyId, prisonEventQueue().queueSecretAccessKey, region)
        .also { log.info("Created aws sqs client for queue ${prisonEventQueue().queueName}") }
        .also { hmppsQueueService.registerHmppsQueue(it, prisonEventQueue().queueName, awsSqsDlqClient, prisonEventQueue().dlqName) }
    }

  @Bean
  fun hmppsAwsSqsClient(sqsConfigProperties: SqsConfigProperties, hmppsAwsSqsDlqClient: AmazonSQS): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(hmppsDomainEventQueue().queueAccessKeyId, hmppsDomainEventQueue().queueSecretAccessKey, region)
        .also { log.info("Created aws sqs client for queue ${hmppsDomainEventQueue().queueName}") }
        .also { hmppsQueueService.registerHmppsQueue(it, hmppsDomainEventQueue().queueName, hmppsAwsSqsDlqClient, hmppsDomainEventQueue().dlqName) }
    }

  @Bean
  fun awsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(prisonEventQueue().dlqAccessKeyId, prisonEventQueue().dlqSecretAccessKey, region)
        .also { log.info("Created aws dlq sqs client for dlq ${prisonEventQueue().dlqName}") }
    }

  @Bean
  fun hmppsAwsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(hmppsDomainEventQueue().dlqAccessKeyId, hmppsDomainEventQueue().dlqSecretAccessKey, region)
        .also { log.info("Created aws dlq sqs client for dlq ${hmppsDomainEventQueue().dlqName}") }
    }

  private fun amazonSQS(accessKeyId: String, secretAccessKey: String, region: String) =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKeyId, secretAccessKey)))
      .withRegion(region)
      .build()
}
