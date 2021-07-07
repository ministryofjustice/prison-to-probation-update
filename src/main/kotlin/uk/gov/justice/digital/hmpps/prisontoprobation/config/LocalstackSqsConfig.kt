package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.SubscribeRequest
import com.amazonaws.services.sqs.AmazonSQS
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.MissingQueueException

@Configuration
@ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
class LocalstackSqsConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  @DependsOn("hmppsQueueService")
  fun awsSnsClient(
    hmppsSqsProperties: HmppsSqsProperties,
    hmppsSnsProperties: HmppsSnsProperties,
    @Qualifier("prisoneventqueue-sqs-client") prisonEventSqsClient: AmazonSQS,
  ): AmazonSNS =
    with(hmppsSqsProperties) {
      val topicName = hmppsSnsProperties.prisonEventTopic().topicName
      val queueName = queues["prisoneventqueue"]?.queueName ?: throw MissingQueueException("Queue prisoneventqueue has not been configured")
      localstackAmazonSNS(localstackUrl, region)
        .also { snsClient -> snsClient.createTopic(topicName) }
        .also { log.info("Created localstack sns topic with name $topicName") }
        .also {
          subscribeToTopic(
            it,
            localstackUrl,
            region,
            topicName,
            queueName,
            mapOf("FilterPolicy" to """{"eventType":[ "PRISONER_RELEASED", "PRISONER_RECEIVED"] }""")
          )
        }
    }

  @Bean
  @DependsOn("hmppsQueueService")
  fun hmppsAwsSnsClient(
    hmppsSqsProperties: HmppsSqsProperties,
    hmppsSnsProperties: HmppsSnsProperties,
    @Qualifier("hmppseventqueue-sqs-client") hmppsEventSqsClient: AmazonSQS,
  ): AmazonSNS =
    with(hmppsSqsProperties) {
      val topicName = hmppsSnsProperties.hmppsEventTopic().topicName
      val queueName = queues["hmppseventqueue"]?.queueName ?: throw MissingQueueException("Queue hmppseventqueue has not been configured")
      localstackAmazonSNS(localstackUrl, region)
        .also { snsClient -> snsClient.createTopic(topicName) }
        .also { log.info("Created localstack sns topic with name $topicName") }
        .also {
          subscribeToTopic(
            it,
            localstackUrl,
            region,
            topicName,
            queueName,
            mapOf("FilterPolicy" to """{"eventType":[ "PRISONER_RELEASED", "PRISONER_RECEIVED"] }""")
          )
        }
    }

  private fun subscribeToTopic(
    awsSnsClient: AmazonSNS,
    localstackUrl: String,
    region: String,
    topicName: String,
    queueName: String,
    attributes: Map<String, String>
  ) =
    awsSnsClient.subscribe(
      SubscribeRequest()
        .withTopicArn(localstackTopicArn(region, topicName))
        .withProtocol("sqs")
        .withEndpoint("$localstackUrl/queue/$queueName")
        .withAttributes(attributes)
    )

  private fun localstackTopicArn(region: String, topicName: String) = "arn:aws:sns:$region:000000000000:$topicName"

  private fun localstackAmazonSNS(localstackUrl: String, region: String) =
    AmazonSNSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(localstackUrl, region))
      .build()
}
