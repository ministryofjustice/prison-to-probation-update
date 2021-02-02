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
import java.time.LocalDateTime

@Configuration
class ReportsConfiguration(
  private val inProgressReport: InProgressReport,
  private val notMatchedReport: NotMatchedReport,
  private val processedReport: ProcessedReport,
  private val matchSummaryReport: MatchSummaryReport,
) {

  @Bean
  @RouterOperations(
    RouterOperation(
      path = "/report/in-progress",
      method = arrayOf(RequestMethod.GET),
      beanClass = InProgressReport::class,
      beanMethod = "generate"
    ),
    RouterOperation(
      path = "/report/not-matched",
      method = arrayOf(RequestMethod.GET),
      beanClass = NotMatchedReport::class,
      beanMethod = "generate"
    ),
    RouterOperation(
      path = "/report/processed",
      method = arrayOf(RequestMethod.GET),
      beanClass = ProcessedReport::class,
      beanMethod = "generate"
    ),
    RouterOperation(
      path = "/report//match-summary",
      method = arrayOf(RequestMethod.GET),
      beanClass = MatchSummaryReport::class,
      beanMethod = "generate"
    ),
  )
  fun router(): RouterFunction<ServerResponse> = router {
    path("/report").nest {
      GET("/in-progress", ::getInProgress)
      GET("/not-matched", ::getNotMatched)
      GET("/processed", ::getProcessed)
      GET("/match-summary", ::getMatchSummary)
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

  fun getProcessed(request: ServerRequest): ServerResponse =
    report {
      processedReport.generate(
        request.param("locationId").orElse(null),
        request.param("eventType").orElse(null),
        request.param("processedDateStartDateTime").map { LocalDateTime.parse(it) }.orElse(null),
        request.param("processedDateEndDateTime").map { LocalDateTime.parse(it) }.orElse(null),
        request.param("createdDateStartDateTime").map { LocalDateTime.parse(it) }.orElse(null),
        request.param("createdDateEndDateTime").map { LocalDateTime.parse(it) }.orElse(null),
      )
    }

  fun getMatchSummary(request: ServerRequest): ServerResponse =
    ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(
      matchSummaryReport.generate(
        request.param("locationId").orElse(null),
        request.param("createdDateStartDateTime").map { LocalDateTime.parse(it) }.orElse(null),
        request.param("createdDateEndDateTime").map { LocalDateTime.parse(it) }.orElse(null),
      )
    )
}

private fun report(report: () -> String): ServerResponse =
  ServerResponse.ok().contentType(MediaType("text", "csv")).body(report())
