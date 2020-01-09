package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.boot.web.client.RootUriTemplateHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails
import org.springframework.web.client.RestTemplate

@Configuration
open class RestTemplateConfiguration(private val apiDetails: ClientCredentialsResourceDetails,
                                     @Value("\${community.endpoint.url}") private val communityRootUri: String,
                                     @Value("\${elite2.endpoint.url}") private val elite2RootUri: String,
                                     @Value("\${oauth.endpoint.url}") private val oauthRootUri: String) {
  @Bean(name = ["oauthApiRestTemplate"])
  open fun oauthRestTemplate(restTemplateBuilder: RestTemplateBuilder): RestTemplate =
      getRestTemplate(restTemplateBuilder, oauthRootUri)

  @Bean(name = ["communityApiRestTemplate"])
  open fun communityRestTemplate(): OAuth2RestTemplate {

    val communityApiRestTemplate = OAuth2RestTemplate(apiDetails)
    RootUriTemplateHandler.addTo(communityApiRestTemplate, communityRootUri)

    return communityApiRestTemplate
  }

  @Bean(name = ["communityApiHealthRestTemplate"])
  open fun communityHealthRestTemplate(restTemplateBuilder: RestTemplateBuilder): RestTemplate =
      getRestTemplate(restTemplateBuilder, communityRootUri)

  @Bean(name = ["elite2ApiHealthRestTemplate"])
  open fun elite2HealthRestTemplate(restTemplateBuilder: RestTemplateBuilder): RestTemplate =
      getRestTemplate(restTemplateBuilder, elite2RootUri)

  @Bean(name = ["elite2ApiRestTemplate"])
  open fun elite2ApiRestTemplate(): OAuth2RestTemplate {

    val elite2ApiRestTemplate = OAuth2RestTemplate(apiDetails)
    RootUriTemplateHandler.addTo(elite2ApiRestTemplate, elite2RootUri)

    return elite2ApiRestTemplate
  }

  private fun getRestTemplate(restTemplateBuilder: RestTemplateBuilder, uri: String?): RestTemplate =
      restTemplateBuilder.rootUri(uri).build()
}
