package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.time.LocalDateTime
import java.time.ZoneOffset

class InProgressReportAPITest : NoQueueListenerIntegrationTest() {

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
  internal fun `can retrieve in progress updates while excluding processsed ones`() {
    messageRepository.save(
      Message(
        bookingId = 97,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        retryCount = 3,
        createdDate = LocalDateTime.parse("2020-12-09T15:15:50"),
        message = """{"text": "value"}""",
        deleteBy = LocalDateTime.parse("2020-12-19T15:15:50").toEpochSecond(
          ZoneOffset.UTC
        ),
        reportable = true,
        processedDate = LocalDateTime.now()
      )
    )
    messageRepository.save(
      Message(
        bookingId = 98,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        retryCount = 3,
        createdDate = LocalDateTime.parse("2020-12-09T15:15:50"),
        message = """{"text": "value"}""",
        deleteBy = LocalDateTime.parse("2020-12-19T15:15:50").toEpochSecond(
          ZoneOffset.UTC
        ),
        reportable = false,
      )
    )
    messageRepository.save(
      Message(
        bookingId = 99,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        retryCount = 3,
        createdDate = LocalDateTime.parse("2020-12-09T15:15:50"),
        message = """{"text": "value"}""",
        deleteBy = LocalDateTime.parse("2020-12-19T15:15:50").toEpochSecond(
          ZoneOffset.UTC
        ),
        reportable = true,
        offenderNo = "A1234GY",
        bookingNo = "12345V",
        matchingCrns = "X12345,X87654",
        status = "BOOKING_NUMBER_NOT_ASSIGNED",
        locationId = "MDI",
        locationDescription = "Moorland HMP",
        recall = false,
        legalStatus = "SENTENCED"
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
        assertThat(it.responseBody).contains(""""BOOKINGID","BOOKINGNO","CREATEDDATE","CRNS","DELETEBY","EVENTTYPE","LEGALSTATUS","LOCATION","LOCATIONID","OFFENDERNO","RECALL","STATUS"""")
        assertThat(it.responseBody).doesNotContain(""""97",""")
        assertThat(it.responseBody).contains(""""98","","2020-12-09T15:15:50","","2020-12-19T15:15:50","IMPRISONMENT_STATUS-CHANGED","","","","","",""""")
        assertThat(it.responseBody).contains(""""99","12345V","2020-12-09T15:15:50","X12345,X87654","2020-12-19T15:15:50","IMPRISONMENT_STATUS-CHANGED","SENTENCED","Moorland HMP","MDI","A1234GY","false","BOOKING_NUMBER_NOT_ASSIGNED"""")
      }
  }
}
