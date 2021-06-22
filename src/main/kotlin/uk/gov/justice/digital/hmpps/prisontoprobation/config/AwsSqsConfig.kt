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
      amazonSQS(dpsQueue.queueAccessKeyId, dpsQueue.queueSecretAccessKey, region)
        .also { log.info("Created aws sqs client for queue ${dpsQueue.queueName}") }
        .also { hmppsQueueService.registerHmppsQueue(it, dpsQueue.queueName, awsSqsDlqClient, dpsQueue.dlqName) }
    }

  @Bean
  fun hmppsAwsSqsClient(sqsConfigProperties: SqsConfigProperties, hmppsAwsSqsDlqClient: AmazonSQS): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(hmppsQueue.queueAccessKeyId, hmppsQueue.queueSecretAccessKey, region)
        .also { log.info("Created aws sqs client for queue ${hmppsQueue.queueName}") }
        .also { hmppsQueueService.registerHmppsQueue(it, hmppsQueue.queueName, hmppsAwsSqsDlqClient, hmppsQueue.dlqName) }
    }

  @Bean
  fun awsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(dpsQueue.dlqAccessKeyId, dpsQueue.dlqSecretAccessKey, region)
        .also { log.info("Created aws dlq sqs client for dlq ${dpsQueue.dlqName}") }
    }

  @Bean
  fun hmppsAwsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(hmppsQueue.dlqAccessKeyId, hmppsQueue.dlqSecretAccessKey, region)
        .also { log.info("Created aws dlq sqs client for dlq ${hmppsQueue.dlqName}") }
    }

  private fun amazonSQS(accessKeyId: String, secretAccessKey: String, region: String) =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKeyId, secretAccessKey)))
      .withRegion(region)
      .build()
}
