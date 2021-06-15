package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
class LocalstackSqsConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean("awsSqsClient")
  fun awsSqsClientLocalstack(sqsConfigProperties: SqsConfigProperties, awsSqsDlqClient: AmazonSQS): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { sqsClient -> createMainQueue(sqsClient, awsSqsDlqClient, dpsQueue.queueName, dpsQueue.dlqName) }
        .also { log.info("Created localstack sqs client for queue ${dpsQueue.queueName}") }
    }

  @Bean("hmppsAwsSqsClient")
  fun hmppsAwsSqsClientLocalstack(sqsConfigProperties: SqsConfigProperties, hmppsAwsSqsDlqClient: AmazonSQS): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { sqsClient -> createMainQueue(sqsClient, hmppsAwsSqsDlqClient, hmppsQueue.queueName, hmppsQueue.dlqName) }
        .also { log.info("Created localstack sqs client for queue ${hmppsQueue.queueName}") }
    }

  @Bean("awsSqsDlqClient")
  fun awsSqsDlqClientLocalstack(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(dpsQueue.dlqName) }
        .also { log.info("Created localstack dlq sqs client for dlq ${dpsQueue.dlqName}") }
    }

  @Bean("hmppsAwsSqsDlqClient")
  fun hmppsAwsSqsDlqClientLocalstack(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(hmppsQueue.dlqName) }
        .also { log.info("Created localstack dlq sqs client for dlq ${hmppsQueue.dlqName}") }
    }

  private fun localstackAmazonSQS(localstackUrl: String, region: String) =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(localstackUrl, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  private fun createMainQueue(
    queueSqsClient: AmazonSQS,
    dlqSqsClient: AmazonSQS,
    queueName: String,
    dlqName: String,
  ) =
    dlqSqsClient.getQueueUrl(dlqName).queueUrl
      .let { dlqQueueUrl -> dlqSqsClient.getQueueAttributes(dlqQueueUrl, listOf(QueueAttributeName.QueueArn.toString())).attributes["QueueArn"]!! }
      .also { queueArn ->
        queueSqsClient.createQueue(
          CreateQueueRequest(queueName).withAttributes(
            mapOf(
              QueueAttributeName.RedrivePolicy.toString() to
                """{"deadLetterTargetArn":"$queueArn","maxReceiveCount":"5"}"""
            )
          )
        )
      }
}
