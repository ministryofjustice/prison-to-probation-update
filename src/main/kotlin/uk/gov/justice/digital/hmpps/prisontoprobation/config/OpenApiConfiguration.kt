package uk.gov.justice.digital.hmpps.prisontoprobation.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
  @Bean
  fun customOpenAPI(buildProperties: BuildProperties): OpenAPI = OpenAPI()
    .info(
      Info().title("Prison to Probation update service")
        .description("A service that synchronises prison custody data to Delius")
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk"))
    )
}
