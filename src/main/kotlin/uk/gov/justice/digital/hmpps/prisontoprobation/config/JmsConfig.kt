package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazon.sqs.javamessaging.ProviderConfiguration
import com.amazon.sqs.javamessaging.SQSConnectionFactory
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.support.destination.DynamicDestinationResolver
import javax.jms.Session

@Configuration
@EnableJms
open class JmsConfig {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  open fun jmsListenerContainerFactory(awsSqs: AmazonSQS): DefaultJmsListenerContainerFactory {
    val factory = DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(SQSConnectionFactory(ProviderConfiguration(), awsSqs))
    factory.setDestinationResolver(DynamicDestinationResolver())
    factory.setConcurrency("1")
    factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
    factory.setErrorHandler { t: Throwable? -> log.error("Error caught in jms listener", t) }
    return factory
  }

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  open fun awsSqsClient(@Value("\${sqs.aws.access.key.id}") accessKey: String?,
                        @Value("\${sqs.aws.secret.access.key}") secretKey: String?,
                        @Value("\${sqs.endpoint.region}") region: String?): AmazonSQS =
      AmazonSQSClientBuilder.standard()
          .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
          .withRegion(region)
          .build()

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "localstack")
  open fun awsLocalClient(@Value("\${sqs.endpoint.url}") serviceEndpoint: String?,
                          @Value("\${sqs.endpoint.region}") region: String?): AmazonSQS =
      AmazonSQSClientBuilder.standard()
          .withEndpointConfiguration(EndpointConfiguration(serviceEndpoint, region))
          .build()
}
