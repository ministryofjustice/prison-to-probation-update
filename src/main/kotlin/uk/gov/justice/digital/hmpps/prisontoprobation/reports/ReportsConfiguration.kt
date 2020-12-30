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
class ReportsConfiguration(
  private val inProgressReport: InProgressReport,
  private val notMatchedReport: NotMatchedReport
) {

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
      GET("/not-matched", ::getNotMatched)
    }
  }

  fun getInProgress(request: ServerRequest): ServerResponse =
    report {
      inProgressReport.generate()
    }

  fun getNotMatched(request: ServerRequest): ServerResponse =
    report {
      notMatchedReport.generate(request.param("daysOld").orElse("7").toLong())
    }
}

private fun report(report: () -> String): ServerResponse =
  ServerResponse.ok().contentType(MediaType("text", "csv")).body(report())
