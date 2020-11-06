package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SmokeTestWebClientConfiguration(@Value("\${smoketest.endpoint.url}") private val smokeTestUrl: String,
                                      private val webClientBuilder: WebClient.Builder) {

  @Bean
  fun smokeTestWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("smoketest-service")

    return webClientBuilder
        .baseUrl(smokeTestUrl)
        .apply(oauth2Client.oauth2Configuration())
        .exchangeStrategies(ExchangeStrategies.builder()
            .codecs { configurer ->
              configurer.defaultCodecs()
                  .maxInMemorySize(-1)
            }
            .build())
        .build()
  }

}


