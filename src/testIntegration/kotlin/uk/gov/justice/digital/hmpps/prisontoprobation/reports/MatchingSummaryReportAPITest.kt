package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.BOOKING_NUMBER_NOT_ASSIGNED
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.COMPLETED
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.ERROR
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.LOCATION_NOT_UPDATED
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.NO_LONGER_VALID
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.NO_MATCH
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.NO_MATCH_WITH_SENTENCE_DATE
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.TOO_MANY_MATCHES
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.VALIDATED
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class MatchingSummaryReportAPITest : NoQueueListenerIntegrationTest() {

  @Test
  internal fun `requires a valid authentication token`() {
    webTestClient.get()
      .uri("/report/match-summary")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  internal fun `without ROLE_PTPU_REPORT access is denied`() {
    webTestClient.get()
      .uri("/report/match-summary")
      .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isForbidden
  }

  @Nested
  inner class TotalsBreakdown {
    @BeforeEach
    internal fun setUp() {
      messageRepository.saveAll(
        listOf(
          aMessage(ageInDays = 1, status = COMPLETED, processedDate = yesterday(), eventType = "IMPRISONMENT_STATUS-CHANGED"),
          aMessage(ageInDays = 1, status = COMPLETED, processedDate = yesterday()),
          aMessage(ageInDays = 8, status = COMPLETED, processedDate = yesterday()),
          aMessage(ageInDays = 1, status = NO_LONGER_VALID, processedDate = yesterday()),
          aMessage(ageInDays = 1, status = VALIDATED, retryCount = 0),
          aMessage(ageInDays = 1, status = NO_MATCH),
          aMessage(ageInDays = 1, status = NO_MATCH_WITH_SENTENCE_DATE),
          aMessage(ageInDays = 1, status = TOO_MANY_MATCHES),
          aMessage(ageInDays = 1, status = BOOKING_NUMBER_NOT_ASSIGNED),
          aMessage(ageInDays = 1, status = LOCATION_NOT_UPDATED),
          aMessage(ageInDays = 1, status = ERROR),
          aMessage(ageInDays = 8, status = NO_MATCH),
          aMessage(ageInDays = 8, status = NO_MATCH_WITH_SENTENCE_DATE),
          aMessage(ageInDays = 8, status = TOO_MANY_MATCHES),
          aMessage(ageInDays = 8, status = BOOKING_NUMBER_NOT_ASSIGNED),
          aMessage(ageInDays = 8, status = LOCATION_NOT_UPDATED),
          aMessage(ageInDays = 8, status = ERROR),
        ),
      )
    }

    @Test
    internal fun `can retrieve a summary report breaking down totals for IMPRISONMENT_STATUS-CHANGED`() {
      webTestClient.get()
        .uri("/report/match-summary")
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("total").isEqualTo(17)
        .jsonPath("completed.total").isEqualTo(4)
        .jsonPath("completed.success").isEqualTo(3)
        .jsonPath("completed.rejected").isEqualTo(1)
        .jsonPath("waiting.total").isEqualTo(7)
        .jsonPath("waiting.new").isEqualTo(1)
        .jsonPath("waiting.retry").isEqualTo(6)
        .jsonPath("waiting.category.no-match").isEqualTo(1)
        .jsonPath("waiting.category.no-match-sentence").isEqualTo(1)
        .jsonPath("waiting.category.too-many-matches").isEqualTo(1)
        .jsonPath("waiting.category.book-number-set-fail").isEqualTo(1)
        .jsonPath("waiting.category.location-set-fail").isEqualTo(1)
        .jsonPath("waiting.category.error-fail").isEqualTo(1)
        .jsonPath("exceeded-sla.total").isEqualTo(6)
        .jsonPath("exceeded-sla.category.no-match").isEqualTo(1)
        .jsonPath("exceeded-sla.category.no-match-sentence").isEqualTo(1)
        .jsonPath("exceeded-sla.category.too-many-matches").isEqualTo(1)
        .jsonPath("exceeded-sla.category.book-number-set-fail").isEqualTo(1)
        .jsonPath("exceeded-sla.category.location-set-fail").isEqualTo(1)
        .jsonPath("exceeded-sla.category.error-fail").isEqualTo(1)
    }

    @Test
    internal fun `adjusting sla adjusts totals in buckets`() {
      webTestClient.get()
        .uri { it.path("/report/match-summary").queryParam("slaDays", "9").build() }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("total").isEqualTo(17)
        .jsonPath("completed.total").isEqualTo(4)
        .jsonPath("completed.success").isEqualTo(3)
        .jsonPath("completed.rejected").isEqualTo(1)
        .jsonPath("waiting.total").isEqualTo(13)
        .jsonPath("waiting.new").isEqualTo(1)
        .jsonPath("waiting.retry").isEqualTo(12)
        .jsonPath("waiting.category.no-match").isEqualTo(2)
        .jsonPath("waiting.category.no-match-sentence").isEqualTo(2)
        .jsonPath("waiting.category.too-many-matches").isEqualTo(2)
        .jsonPath("waiting.category.book-number-set-fail").isEqualTo(2)
        .jsonPath("waiting.category.location-set-fail").isEqualTo(2)
        .jsonPath("waiting.category.error-fail").isEqualTo(2)
        .jsonPath("exceeded-sla.total").isEqualTo(0)
        .jsonPath("exceeded-sla.category.no-match").isEqualTo(0)
        .jsonPath("exceeded-sla.category.no-match-sentence").isEqualTo(0)
        .jsonPath("exceeded-sla.category.too-many-matches").isEqualTo(0)
        .jsonPath("exceeded-sla.category.book-number-set-fail").isEqualTo(0)
        .jsonPath("exceeded-sla.category.location-set-fail").isEqualTo(0)
        .jsonPath("exceeded-sla.category.error-fail").isEqualTo(0)
    }
  }

  @Test
  internal fun `can filter by prison`() {
    messageRepository.saveAll(
      listOf(
        aMessage(ageInDays = 1, status = COMPLETED, processedDate = yesterday(), locationId = "MDI"),
        aMessage(ageInDays = 1, status = COMPLETED, processedDate = yesterday(), locationId = "MDI"),
        aMessage(ageInDays = 1, status = COMPLETED, processedDate = yesterday(), locationId = "WWI"),
      ),
    )
    webTestClient.get()
      .uri("/report/match-summary")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(3)

    webTestClient.get()
      .uri { it.path("/report/match-summary").queryParam("locationId", "MDI").build() }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(2)

    webTestClient.get()
      .uri { it.path("/report/match-summary").queryParam("locationId", "WWI").build() }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(1)
  }

  @Test
  internal fun `can filter by earliest created date`() {
    messageRepository.saveAll(
      listOf(
        aMessage(ageInDays = 1, status = COMPLETED, processedDate = yesterday()),
        aMessage(ageInDays = 5, status = COMPLETED, processedDate = yesterday()),
        aMessage(ageInDays = 10, status = COMPLETED, processedDate = yesterday()),
      ),
    )
    webTestClient.get()
      .uri("/report/match-summary")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(3)

    webTestClient.get()
      .uri {
        it.path("/report/match-summary").queryParam("createdDateStartDateTime", daysAgo(11))
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(3)

    webTestClient.get()
      .uri {
        it.path("/report/match-summary").queryParam("createdDateStartDateTime", daysAgo(6))
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(2)

    webTestClient.get()
      .uri {
        it.path("/report/match-summary").queryParam("createdDateStartDateTime", daysAgo(2))
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(1)
  }

  @Test
  internal fun `can filter by latest created date`() {
    messageRepository.saveAll(
      listOf(
        aMessage(ageInDays = 1, status = COMPLETED, processedDate = yesterday()),
        aMessage(ageInDays = 5, status = COMPLETED, processedDate = yesterday()),
        aMessage(ageInDays = 10, status = COMPLETED, processedDate = yesterday()),
      ),
    )
    webTestClient.get()
      .uri("/report/match-summary")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(3)

    webTestClient.get()
      .uri {
        it.path("/report/match-summary").queryParam("createdDateEndDateTime", daysAgo(11))
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(0)

    webTestClient.get()
      .uri {
        it.path("/report/match-summary").queryParam("createdDateEndDateTime", daysAgo(9))
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(1)

    webTestClient.get()
      .uri {
        it.path("/report/match-summary").queryParam("createdDateEndDateTime", daysAgo(4))
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(2)

    webTestClient.get()
      .uri {
        it.path("/report/match-summary").queryParam("createdDateEndDateTime", daysAgo(0))
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("total").isEqualTo(3)
  }

  private fun daysAgo(days: Long) = LocalDateTime.now().minusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  fun aMessage(
    ageInDays: Long,
    status: SynchroniseState,
    processedDate: LocalDateTime? = null,
    locationId: String = "MDI",
    retryCount: Int = 99,
    eventType: String = "IMPRISONMENT_STATUS-CHANGED",
  ): Message = Message(
    bookingId = Random.nextLong(),
    eventType = eventType,
    retryCount = retryCount,
    createdDate = LocalDateTime.now().minusDays(ageInDays),
    message = """{"text": "value"}""",
    deleteBy = LocalDateTime.now().plusDays(30).toEpochSecond(ZoneOffset.UTC),
    reportable = true,
    offenderNo = "A1234GA",
    bookingNo = "12345A",
    matchingCrns = "",
    status = status.name,
    locationId = locationId,
    locationDescription = "Moorland HMP",
    recall = false,
    legalStatus = "SENTENCED",
    processedDate = processedDate,
  )
}

private fun yesterday() = LocalDateTime.now().minusDays(1)
