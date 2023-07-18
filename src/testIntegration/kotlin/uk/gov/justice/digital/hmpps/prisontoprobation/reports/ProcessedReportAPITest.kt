package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDateTime
import java.time.ZoneOffset

class ProcessedReportAPITest : NoQueueListenerIntegrationTest() {

  @Test
  internal fun `requires a valid authentication token`() {
    webTestClient.get()
      .uri("/report/processed")
      .accept(MediaType.TEXT_PLAIN)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  internal fun `without ROLE_PTPU_REPORT access is denied`() {
    webTestClient.get()
      .uri("/report/processed")
      .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
      .accept(MediaType.TEXT_PLAIN)
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  internal fun `can retrieve in processed records`() {
    messageRepository.save(
      Message(
        bookingId = 97,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        retryCount = 3,
        createdDate = LocalDateTime.parse("2020-12-09T15:15:50"),
        message = """{"text": "value"}""",
        deleteBy = LocalDateTime.parse("2020-12-19T15:15:50").toEpochSecond(
          ZoneOffset.UTC,
        ),
        reportable = true,
        locationId = "MDI",
        locationDescription = "HMP Moorland",
        processedDate = LocalDateTime.parse("2020-12-10T15:15:50"),
        legalStatus = "SENTENCED",
        bookingNo = "12376T",
        matchingCrns = "X12345",
        recall = false,
        offenderNo = "A1234DY",
        status = "MATCHED",
      ),
    )
    messageRepository.save(
      Message(
        bookingId = 98,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        retryCount = 3,
        createdDate = LocalDateTime.parse("2020-12-09T15:15:50"),
        message = """{"text": "value"}""",
        deleteBy = LocalDateTime.parse("2020-12-19T15:15:50").toEpochSecond(
          ZoneOffset.UTC,
        ),
        reportable = false,
      ),
    )

    webTestClient.get()
      .uri("/report/processed")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType("text", "csv"))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .consumeWith {
        assertThat(it.responseBody).contains(""""BOOKINGID","BOOKINGNO","CREATEDDATE","CRNS","DELETEBY","EVENTTYPE","LEGALSTATUS","LOCATION","LOCATIONID","OFFENDERNO","PROCESSEDDATE","RECALL","STATUS"""")
        assertThat(it.responseBody).contains(""""97","12376T","2020-12-09T15:15:50","X12345","2020-12-19T15:15:50","IMPRISONMENT_STATUS-CHANGED","SENTENCED","HMP Moorland","MDI","A1234DY","2020-12-10T15:15:50","false","MATCHED"""")
        assertThat(it.responseBody).doesNotContain(""""98",""")
      }
  }

  @Test
  internal fun `can filter by prison code`() {
    messageRepository.saveAll(
      listOf(
        aMessage(90L, locationId = "MDI"),
        aMessage(91L, locationId = "MDI"),
        aMessage(92L, locationId = "WWI"),
      ),
    )

    webTestClient.get()
      .uri { it.path("/report/processed").queryParam("locationId", "MDI").build() }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType("text", "csv"))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .consumeWith {
        assertThat(it.responseBody).contains(""""90",""")
        assertThat(it.responseBody).contains(""""91",""")
        assertThat(it.responseBody).doesNotContain(""""92",""")
      }
  }

  @Test
  internal fun `can filter by event type`() {
    messageRepository.saveAll(
      listOf(
        aMessage(90L, eventType = "BOOKING_NUMBER-CHANGED"),
        aMessage(91L, eventType = "BOOKING_NUMBER-CHANGED"),
        aMessage(92L, eventType = "IMPRISONMENT_STATUS-CHANGED"),
        aMessage(93L, eventType = "IMPRISONMENT_STATUS-CHANGED"),
      ),
    )

    webTestClient.get()
      .uri { it.path("/report/processed").queryParam("eventType", "BOOKING_NUMBER-CHANGED").build() }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType("text", "csv"))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .consumeWith {
        assertThat(it.responseBody).contains(""""90",""")
        assertThat(it.responseBody).contains(""""91",""")
        assertThat(it.responseBody).doesNotContain(""""92",""")
        assertThat(it.responseBody).doesNotContain(""""93",""")
      }

    webTestClient.get()
      .uri { it.path("/report/processed").queryParam("eventType", "IMPRISONMENT_STATUS-CHANGED").build() }
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
      .accept(MediaType("text", "csv"))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .consumeWith {
        assertThat(it.responseBody).doesNotContain(""""90",""")
        assertThat(it.responseBody).doesNotContain(""""91",""")
        assertThat(it.responseBody).contains(""""92",""")
        assertThat(it.responseBody).contains(""""93",""")
      }
  }

  @Nested
  inner class ProcessedDateFiltering {
    @BeforeEach
    internal fun setUp() {
      messageRepository.saveAll(
        listOf(
          aMessage(90L, processedDate = LocalDateTime.parse("2020-12-10T15:15:50")),
          aMessage(91L, processedDate = LocalDateTime.parse("2020-12-11T15:15:50")),
          aMessage(92L, processedDate = LocalDateTime.parse("2020-12-12T15:15:50")),
          aMessage(93L, processedDate = LocalDateTime.parse("2020-12-13T15:15:50")),
        ),
      )
    }

    @Test
    internal fun `can filter by processed date range`() {
      webTestClient.get()
        .uri {
          it.path("/report/processed")
            .queryParam("processedDateStartDateTime", "2020-12-11T15:15:49")
            .queryParam("processedDateEndDateTime", "2020-12-12T15:15:51")
            .build()
        }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          assertThat(it.responseBody).doesNotContain(""""90",""")
          assertThat(it.responseBody).contains(""""91",""")
          assertThat(it.responseBody).contains(""""92",""")
          assertThat(it.responseBody).doesNotContain(""""93",""")
        }
    }

    @Test
    internal fun `can filter by just processed start date range`() {
      webTestClient.get()
        .uri {
          it.path("/report/processed")
            .queryParam("processedDateStartDateTime", "2020-12-11T15:15:49")
            .build()
        }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          assertThat(it.responseBody).doesNotContain(""""90",""")
          assertThat(it.responseBody).contains(""""91",""")
          assertThat(it.responseBody).contains(""""92",""")
          assertThat(it.responseBody).contains(""""93",""")
        }
    }

    @Test
    internal fun `can filter by just processed end date range`() {
      webTestClient.get()
        .uri {
          it.path("/report/processed")
            .queryParam("processedDateEndDateTime", "2020-12-11T15:15:51")
            .build()
        }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          assertThat(it.responseBody).contains(""""90",""")
          assertThat(it.responseBody).contains(""""91",""")
          assertThat(it.responseBody).doesNotContain(""""92",""")
          assertThat(it.responseBody).doesNotContain(""""93",""")
        }
    }
  }

  @Nested
  inner class CreatedDateFiltering {
    @BeforeEach
    internal fun setUp() {
      messageRepository.saveAll(
        listOf(
          aMessage(90L, createdDate = LocalDateTime.parse("2020-12-10T15:15:50")),
          aMessage(91L, createdDate = LocalDateTime.parse("2020-12-11T15:15:50")),
          aMessage(92L, createdDate = LocalDateTime.parse("2020-12-12T15:15:50")),
          aMessage(93L, createdDate = LocalDateTime.parse("2020-12-13T15:15:50")),
        ),
      )
    }

    @Test
    internal fun `can filter by created date range`() {
      webTestClient.get()
        .uri {
          it.path("/report/processed")
            .queryParam("createdDateStartDateTime", "2020-12-11T15:15:49")
            .queryParam("createdDateEndDateTime", "2020-12-12T15:15:51")
            .build()
        }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          assertThat(it.responseBody).doesNotContain(""""90",""")
          assertThat(it.responseBody).contains(""""91",""")
          assertThat(it.responseBody).contains(""""92",""")
          assertThat(it.responseBody).doesNotContain(""""93",""")
        }
    }

    @Test
    internal fun `can filter by just created start date range`() {
      webTestClient.get()
        .uri {
          it.path("/report/processed")
            .queryParam("createdDateStartDateTime", "2020-12-11T15:15:49")
            .build()
        }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          assertThat(it.responseBody).doesNotContain(""""90",""")
          assertThat(it.responseBody).contains(""""91",""")
          assertThat(it.responseBody).contains(""""92",""")
          assertThat(it.responseBody).contains(""""93",""")
        }
    }

    @Test
    internal fun `can filter by just created end date range`() {
      webTestClient.get()
        .uri {
          it.path("/report/processed")
            .queryParam("createdDateEndDateTime", "2020-12-11T15:15:51")
            .build()
        }
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_REPORT")))
        .accept(MediaType("text", "csv"))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .consumeWith {
          assertThat(it.responseBody).contains(""""90",""")
          assertThat(it.responseBody).contains(""""91",""")
          assertThat(it.responseBody).doesNotContain(""""92",""")
          assertThat(it.responseBody).doesNotContain(""""93",""")
        }
    }
  }

  fun aMessage(
    bookingId: Long,
    locationId: String = "MDI",
    processedDate: LocalDateTime = LocalDateTime.parse("2020-12-10T15:15:50"),
    createdDate: LocalDateTime = LocalDateTime.parse("2020-12-09T15:15:50"),
    eventType: String = "IMPRISONMENT_STATUS-CHANGED",
  ) = Message(
    bookingId = bookingId,
    eventType = eventType,
    retryCount = 3,
    createdDate = createdDate,
    message = """{"text": "value"}""",
    deleteBy = LocalDateTime.parse("2020-12-19T15:15:50").toEpochSecond(
      ZoneOffset.UTC,
    ),
    reportable = true,
    locationId = locationId,
    locationDescription = "HMP Moorland",
    processedDate = processedDate,
    legalStatus = "SENTENCED",
    bookingNo = "12376T",
    matchingCrns = "X12345",
    recall = false,
    offenderNo = "A1234DY",
    status = "MATCHED",
  )
}
