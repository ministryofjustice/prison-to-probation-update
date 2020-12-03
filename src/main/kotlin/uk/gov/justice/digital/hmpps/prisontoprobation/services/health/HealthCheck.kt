package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

abstract class HealthCheck(private val webClient: WebClient) : HealthIndicator {

  override fun health(): Health? {
    return webClient.get()
      .uri("/health/ping")
      .retrieve()
      .toEntity(String::class.java)
      .flatMap { Mono.just(Health.up().withDetail("HttpStatus", it?.statusCode).build()) }
      .onErrorResume(WebClientResponseException::class.java) { Mono.just(Health.down(it).withDetail("body", it.responseBodyAsString).withDetail("HttpStatus", it.statusCode).build()) }
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
class MessageTable
constructor(@Qualifier("amazonDynamoDB") dynamoDB: AmazonDynamoDB, @Value("\${dynamodb.tableName}") tableName: String) : DynamoDBHealthCheck(dynamoDB, tableName)

@Component
class ScheduleTable
constructor(@Qualifier("scheduleDynamoDB") dynamoDB: AmazonDynamoDB, @Value("\${dynamodb.schedule.tableName}") tableName: String) : DynamoDBHealthCheck(dynamoDB, tableName)
