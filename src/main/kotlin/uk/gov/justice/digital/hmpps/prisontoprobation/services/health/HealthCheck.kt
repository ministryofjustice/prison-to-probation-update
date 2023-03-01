package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthContributorRegistry
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisontoprobation.config.DynamoDbConfigProperties
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties

abstract class HealthCheck(private val webClient: WebClient) : HealthIndicator {

  override fun health(): Health? {
    return webClient.get()
      .uri("/health/ping")
      .retrieve()
      .toEntity(String::class.java)
      .flatMap { Mono.just(Health.up().withDetail("HttpStatus", it?.statusCode).build()) }
      .onErrorResume(WebClientResponseException::class.java) {
        Mono.just(
          Health.down(it).withDetail("body", it.responseBodyAsString).withDetail("HttpStatus", it.statusCode).build()
        )
      }
      .onErrorResume(Exception::class.java) { Mono.just(Health.down(it).build()) }
      .block()
  }
}

@Component
class PrisonApiHealth
constructor(@Qualifier("prisonApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)

@Component
class CommunityApiHealth
constructor(@Qualifier("probationApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)

@Component
class SearchApiHealth
constructor(@Qualifier("searchApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)

@Component
class OAuthApiHealth
constructor(@Qualifier("oauthApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)

@Component
class MessageTable(
  @Qualifier("amazonDynamoDB") dynamoDB: AmazonDynamoDB,
  dynamoDbConfigProperties: DynamoDbConfigProperties
) : DynamoDBHealthCheck(dynamoDB, dynamoDbConfigProperties.tableName)

@Component
class ScheduleTable(
  @Qualifier("scheduleDynamoDB") dynamoDB: AmazonDynamoDB,
  dynamoDbConfigProperties: DynamoDbConfigProperties
) : DynamoDBHealthCheck(dynamoDB, dynamoDbConfigProperties.scheduleTableName)

@Configuration
class QueueHealthConfig(
  private val properties: HmppsSqsProperties,
  private val context: ConfigurableApplicationContext,
  private val healthContributorRegistry: HealthContributorRegistry,
) : ApplicationListener<ApplicationStartedEvent> {

  override fun onApplicationEvent(event: ApplicationStartedEvent) {
    properties.queues.forEach {
      val bean = context.getBean("${it.key}-health") as HealthIndicator
      healthContributorRegistry.registerContributor("${it.key}-health", bean)
    }
  }
}
