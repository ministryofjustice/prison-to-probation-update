package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazon.sqs.javamessaging.ProviderConfiguration
import com.amazon.sqs.javamessaging.SQSConnectionFactory
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.support.destination.DynamicDestinationResolver
import javax.jms.Session

@ConstructorBinding
@ConfigurationProperties(prefix = "hmpps.sqs")
data class SqsConfigProperties(
  val region: String,
  val provider: String,
  val localstackUrl: String = "",
  val dpsQueue: QueueConfig,
  val hmppsQueue: QueueConfig,
) {
  data class QueueConfig(
    val queueName: String,
    val queueAccessKeyId: String = "",
    val queueSecretAccessKey: String = "",
    val dlqName: String,
    val dlqAccessKeyId: String = "",
    val dlqSecretAccessKey: String = "",
  )
}

@Configuration
@EnableJms
class JmsConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun jmsListenerContainerFactory(awsSqsClient: AmazonSQS): DefaultJmsListenerContainerFactory = createContainerFactory(awsSqsClient, "Prison Events")

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun hmppsJmsListenerContainerFactory(hmppsAwsSqsClient: AmazonSQS): DefaultJmsListenerContainerFactory =
    createContainerFactory(hmppsAwsSqsClient, "HMPPS Events")

  private fun createContainerFactory(awsSqsClient: AmazonSQS, name: String): DefaultJmsListenerContainerFactory {
    val factory = DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(SQSConnectionFactory(ProviderConfiguration(), awsSqsClient))
    factory.setDestinationResolver(DynamicDestinationResolver())
    factory.setConcurrency("1")
    factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
    factory.setErrorHandler { t: Throwable? -> log.error("Error caught in $name jms listener", t) }
    return factory
  }

  @Bean
  @ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "aws")
  fun awsSqsClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(dpsQueue.queueAccessKeyId, dpsQueue.queueSecretAccessKey, region)
        .also { log.info("Created sqs client for queue ${dpsQueue.queueName}") }
    }

  @Bean
  @ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "aws")
  fun hmppsAwsSqsClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(hmppsQueue.queueAccessKeyId, hmppsQueue.queueSecretAccessKey, region)
        .also { log.info("Created sqs client for queue ${hmppsQueue.queueName}") }
    }

  @Bean
  @ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "aws")
  fun awsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(dpsQueue.dlqAccessKeyId, dpsQueue.dlqSecretAccessKey, region)
        .also { log.info("Created dlq sqs client for dlq ${dpsQueue.dlqName}") }
    }

  @Bean
  @ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "aws")
  fun hmppsAwsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      amazonSQS(hmppsQueue.dlqAccessKeyId, hmppsQueue.dlqSecretAccessKey, region)
        .also { log.info("Created dlq sqs client for dlq ${hmppsQueue.dlqName}") }
    }

  private fun amazonSQS(accessKeyId: String, secretAccessKey: String, region: String) =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKeyId, secretAccessKey)))
      .withRegion(region)
      .build()

  @Bean("awsSqsClient")
  @ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
  fun awsSqsClientLocalstack(sqsConfigProperties: SqsConfigProperties, awsSqsDlqClient: AmazonSQS): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { sqsClient -> createMainQueue(sqsClient, awsSqsDlqClient, dpsQueue.queueName, dpsQueue.dlqName) }
        .also { log.info("Created localstack sqs client for queue ${dpsQueue.queueName}") }
    }

  @Bean("hmppsAwsSqsClient")
  @ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
  fun hmppsAwsSqsClientLocalstack(sqsConfigProperties: SqsConfigProperties, hmppsAwsSqsDlqClient: AmazonSQS): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { sqsClient -> createMainQueue(sqsClient, hmppsAwsSqsDlqClient, hmppsQueue.queueName, hmppsQueue.dlqName) }
        .also { log.info("Created localstack sqs client for queue ${hmppsQueue.queueName}") }
    }

  @Bean("awsSqsDlqClient")
  @ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
  fun awsSqsDlqClientLocalstack(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(dpsQueue.dlqName) }
        .also { log.info("Created localstack dlq sqs client for dlq ${dpsQueue.dlqName}") }
    }

  @Bean("hmppsAwsSqsDlqClient")
  @ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
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
