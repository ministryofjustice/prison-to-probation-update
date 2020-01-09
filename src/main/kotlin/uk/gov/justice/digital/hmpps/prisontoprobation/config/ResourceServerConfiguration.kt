package uk.gov.justice.digital.hmpps.prisontoprobation.config


import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer
import org.springframework.security.oauth2.provider.token.DefaultTokenServices
import org.springframework.security.oauth2.provider.token.TokenStore
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
open class ResourceServerConfiguration : ResourceServerConfigurerAdapter() {

  @Value("\${jwt.public.key}")
  private val jwtPublicKey: String? = null

  @Autowired(required = false)
  private val buildProperties: BuildProperties? = null

  /**
   * @return health data. Note this is unsecured so no sensitive data allowed!
   */
  private val version: String
    get() = if (buildProperties == null) "version not available" else buildProperties.version

  @Throws(Exception::class)
  override fun configure(http: HttpSecurity) {

    http.headers().frameOptions().sameOrigin().and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

        // Can't have CSRF protection as requires session
        .and().csrf().disable()
        .authorizeRequests()
        .antMatchers("/webjars/**", "/favicon.ico", "/csrf",
            "/health", "/info", "/health/ping").permitAll()
        .anyRequest()
        .authenticated()
  }

  override fun configure(config: ResourceServerSecurityConfigurer) {
    config.tokenServices(tokenServices())
  }

  @Bean
  open fun tokenStore(): TokenStore = JwtTokenStore(accessTokenConverter())

  @Bean
  open fun accessTokenConverter(): JwtAccessTokenConverter {
    val converter = JwtAccessTokenConverter()
    converter.setVerifierKey(String(Base64.decodeBase64(jwtPublicKey)))
    return converter
  }

  @Bean
  @Primary
  open fun tokenServices(): DefaultTokenServices {
    val defaultTokenServices = DefaultTokenServices()
    defaultTokenServices.setTokenStore(tokenStore())
    return defaultTokenServices
  }

  @Bean
  @ConfigurationProperties("prisontoprobation.client")
  open fun prisonToProbationClientCredentials(): ClientCredentialsResourceDetails = ClientCredentialsResourceDetails()
}
