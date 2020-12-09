package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisontoprobation.IntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.time.LocalDateTime
import java.time.ZoneOffset

class InProgressReportAPITest : IntegrationTest() {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  private lateinit var messageRepository: MessageRepository

  @Test
  internal fun `requires a valid authentication token`() {
    webTestClient.get()
      .uri("/report/in-progress")
      .accept(MediaType.TEXT_PLAIN)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  internal fun `without ROLE_PTPU_REPORT access is denied`() {
    webTestClient.get()
      .uri("/report/in-progress")
      .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
      .accept(MediaType.TEXT_PLAIN)
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  internal fun `can retrieve report with role ROLE_PTPU_REPORT`() {
    messageRepository.save(
      Message(
        bookingId = 99,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        retryCount = 3,
        createdDate = LocalDateTime.parse("2020-12-09T15:15:50"),
        message =
          """{"text": "value"}""",
        deleteBy = LocalDateTime.parse("2020-12-19T15:15:50").toEpochSecond(
          ZoneOffset.UTC
        )
      )
    )

    webTestClient.get()
      .uri("/report/in-progress")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType("text", "csv"))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .consumeWith {
        assertThat(it.responseBody).contains(""""BOOKINGID","CREATEDDATE","DELETEBY","EVENTTYPE"""")
        assertThat(it.responseBody).contains(""""99","2020-12-09T15:15:50","2020-12-19T15:15:50","IMPRISONMENT_STATUS-CHANGED"""")
      }
  }
}
