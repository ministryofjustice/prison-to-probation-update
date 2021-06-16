package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.SubscribeRequest
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

  @Bean
  fun awsSnsClient(sqsConfigProperties: SqsConfigProperties): AmazonSNS =
    with(sqsConfigProperties) {
      localstackAmazonSNS(snsUrl, region)
        .also { snsClient -> snsClient.createTopic(dpsQueue.topicName) }
        .also { log.info("Created localstack sns topic with name ${dpsQueue.topicName}") }
    }

  @Bean("awsSqsClient")
  fun awsSqsClient(
    sqsConfigProperties: SqsConfigProperties,
    awsSqsDlqClient: AmazonSQS,
    awsSnsClient: AmazonSNS,
  ): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(sqsUrl, region)
        .also { sqsClient -> createMainQueue(sqsClient, awsSqsDlqClient, dpsQueue.queueName, dpsQueue.dlqName) }
        .also { log.info("Created localstack sqs client for queue ${dpsQueue.queueName}") }
        .also {
          subscribeToTopic(
            awsSnsClient, snsUrl, region, dpsQueue.topicName, dpsQueue.queueName,
            mapOf("FilterPolicy" to """{"eventType":[ "EXTERNAL_MOVEMENT_RECORD-INSERTED", "IMPRISONMENT_STATUS-CHANGED", "SENTENCE_DATES-CHANGED", "BOOKING_NUMBER-CHANGED"] }""")
          )
        }
        .also { log.info("Queue ${dpsQueue.queueName} has subscribed to dps topic ${dpsQueue.topicName}") }
    }

  @Bean("awsSqsDlqClient")
  fun awsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(sqsUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(dpsQueue.dlqName) }
        .also { log.info("Created localstack dlq sqs client for dlq ${dpsQueue.dlqName}") }
    }

  @Bean
  fun hmppsAwsSnsClient(sqsConfigProperties: SqsConfigProperties): AmazonSNS =
    with(sqsConfigProperties) {
      localstackAmazonSNS(sqsConfigProperties.snsUrl, sqsConfigProperties.region)
        .also { snsClient -> snsClient.createTopic(hmppsQueue.topicName) }
        .also { log.info("Created localstack sns topic with name ${hmppsQueue.topicName}") }
    }

  @Bean("hmppsAwsSqsClient")
  fun hmppsAwsSqsClient(
    sqsConfigProperties: SqsConfigProperties,
    hmppsAwsSqsDlqClient: AmazonSQS,
    hmppsAwsSnsClient: AmazonSNS,
  ): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(sqsUrl, region)
        .also { sqsClient -> createMainQueue(sqsClient, hmppsAwsSqsDlqClient, hmppsQueue.queueName, hmppsQueue.dlqName) }
        .also { log.info("Created localstack sqs client for queue ${hmppsQueue.queueName}") }
        .also {
          subscribeToTopic(
            hmppsAwsSnsClient, snsUrl, region, hmppsQueue.topicName, hmppsQueue.queueName,
            mapOf("FilterPolicy" to """{"eventType":[ "PRISONER_RELEASED", "PRISONER_RECEIVED"] }""")
          )
        }
        .also { log.info("Queue ${hmppsQueue.queueName} has subscribed to hmpps topic ${hmppsQueue.topicName}") }
    }

  @Bean("hmppsAwsSqsDlqClient")
  fun hmppsAwsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(sqsUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(hmppsQueue.dlqName) }
        .also { log.info("Created localstack dlq sqs client for dlq ${hmppsQueue.dlqName}") }
    }

  private fun subscribeToTopic(
    awsSnsClient: AmazonSNS,
    snsUrl: String,
    region: String,
    topicName: String,
    queueName: String,
    attributes: Map<String, String>
  ) =
    awsSnsClient.subscribe(
      SubscribeRequest()
        .withTopicArn(localstackTopicArn(region, topicName))
        .withProtocol("sqs")
        .withEndpoint("$snsUrl/queue/$queueName")
        .withAttributes(attributes)
    )

  private fun localstackTopicArn(region: String, topicName: String) = "arn:aws:sns:$region:000000000000:$topicName"

  private fun localstackAmazonSNS(localstackUrl: String, region: String) =
    AmazonSNSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(localstackUrl, region))
      .build()

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
