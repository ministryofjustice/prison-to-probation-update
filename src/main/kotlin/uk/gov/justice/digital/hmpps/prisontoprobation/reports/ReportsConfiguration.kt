package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.router

@Configuration
class ReportsConfiguration(private val inProgressReport: InProgressReport) {

  @Bean
  @RouterOperations(
    RouterOperation(
      path = "/report/in-progress",
      method = arrayOf(RequestMethod.GET),
      beanClass = InProgressReport::class,
      beanMethod = "generate"
    )
  )
  fun router(): RouterFunction<ServerResponse> = router {
    path("/report").nest {
      GET("/in-progress", ::getInProgress)
    }
  }

  fun getInProgress(request: ServerRequest): ServerResponse =
    report {
      inProgressReport.generate()
    }
}

private fun report(report: () -> String): ServerResponse =
  ServerResponse.ok().contentType(MediaType("text", "csv")).body(report())
