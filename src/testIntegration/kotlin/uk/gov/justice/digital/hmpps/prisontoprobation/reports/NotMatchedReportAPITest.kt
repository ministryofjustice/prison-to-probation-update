package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDateTime
import java.time.ZoneOffset

class NotMatchedReportAPITest : NoQueueListenerIntegrationTest() {

  @Test
  internal fun `requires a valid authentication token`() {
    webTestClient.get()
      .uri("/report/not-matched")
      .accept(MediaType.TEXT_PLAIN)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  internal fun `without ROLE_PTPU_REPORT access is denied`() {
    webTestClient.get()
      .uri("/report/not-matched")
      .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
      .accept(MediaType.TEXT_PLAIN)
      .exchange()
      .expectStatus().isForbidden
  }

  @Nested
  inner class WithCorrectRole {
    @BeforeEach
    internal fun setUp() {
      messageRepository.save(
        aMessage(1, 10, "NO_MATCH")
      )
      messageRepository.save(
        aMessage(2, 10, "NO_MATCH_WITH_SENTENCE_DATE")
      )
      messageRepository.save(
        aMessage(3, 10, "TOO_MANY_MATCHES")
      )
      messageRepository.save(
        aMessage(4, 10, "BOOKING_NUMBER_NOT_ASSIGNED")
      )
      messageRepository.save(
        aMessage(5, 0, "NO_MATCH")
      )
    }

    @Test
    internal fun `will retrieve only records not matched over 7 days old`() {

      webTestClient.get()
        .uri("/report/not-matched")
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          Assertions.assertThat(it.responseBody)
            .contains(""""BOOKINGID","BOOKINGNO","CREATEDDATE","CRNS","DELETEBY","EVENTTYPE","LEGALSTATUS","LOCATION","LOCATIONID","OFFENDERNO","RECALL","STATUS"""")
          Assertions.assertThat(it.responseBody)
            .contains(""","IMPRISONMENT_STATUS-CHANGED","SENTENCED","Moorland HMP","MDI","A1234GA","false","NO_MATCH"""")
          Assertions.assertThat(it.responseBody).contains(""""1",""")
          Assertions.assertThat(it.responseBody).contains(""""2",""")
          Assertions.assertThat(it.responseBody).contains(""""3",""")
          Assertions.assertThat(it.responseBody).doesNotContain(""""4",""")
          Assertions.assertThat(it.responseBody).doesNotContain(""""5",""")
        }
    }

    @Test
    internal fun `will retrieve records as old as specified`() {
      webTestClient.get()
        .uri { it.path("/report/not-matched").queryParam("daysOld", "0").build() }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          Assertions.assertThat(it.responseBody).contains(""""1",""")
          Assertions.assertThat(it.responseBody).contains(""""2",""")
          Assertions.assertThat(it.responseBody).contains(""""3",""")
          Assertions.assertThat(it.responseBody).doesNotContain(""""4",""")
          Assertions.assertThat(it.responseBody).contains(""""5",""")
        }
    }

    @Test
    internal fun `will be an empty file if not records found`() {

      webTestClient.get()
        .uri { it.path("/report/not-matched").queryParam("daysOld", "11").build() }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          Assertions.assertThat(it.responseBody).isNull()
        }
    }
  }
}

fun aMessage(bookingId: Long, ageInDays: Long, status: String): Message = Message(
  bookingId = bookingId,
  eventType = "IMPRISONMENT_STATUS-CHANGED",
  retryCount = 99,
  createdDate = LocalDateTime.now().minusDays(ageInDays),
  message = """{"text": "value"}""",
  deleteBy = LocalDateTime.now().plusDays(30).toEpochSecond(ZoneOffset.UTC),
  reportable = true,
  offenderNo = "A1234GA",
  bookingNo = "12345A",
  matchingCrns = "",
  status = status,
  locationId = "MDI",
  locationDescription = "Moorland HMP",
  recall = false,
  legalStatus = "SENTENCED",
  processedDate = null,
)
