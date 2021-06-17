package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazon.sqs.javamessaging.ProviderConfiguration
import com.amazon.sqs.javamessaging.SQSConnectionFactory
import com.amazonaws.services.sqs.AmazonSQS
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.support.destination.DynamicDestinationResolver
import javax.jms.Session

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
}
