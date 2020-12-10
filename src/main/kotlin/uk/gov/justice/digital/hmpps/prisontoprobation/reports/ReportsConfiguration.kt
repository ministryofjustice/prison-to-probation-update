package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.router

@Configuration
class ReportsConfiguration(private val inProgressReport: InProgressReport) {

  @Bean
  fun router(): RouterFunction<ServerResponse> {
    return router {
      path("/report").nest {
        GET("/in-progress", ::getInProgress)
      }
    }
  }

  fun getInProgress(request: ServerRequest): ServerResponse =
    report {
      inProgressReport.generate()
    }
}

private fun report(report: () -> String): ServerResponse =
  ServerResponse.ok().contentType(MediaType("text", "csv")).body(report())
