package uk.gov.justice.digital.hmpps.prisontoprobation

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.services.QueueAdminService
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class HouseKeepingIntegrationTest : QueueIntegrationTest() {
  @Inject
  private lateinit var messageRepository: MessageRepository

  @Inject
  private lateinit var queueAdminService: QueueAdminService

  @BeforeEach
  internal fun setUp() {
    messageRepository.deleteAll()
    // wait until our queues have been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
  }

  @Test
  fun `housekeeping will consume a booking changed message on the dlq and return to main queue`() {
    val message = "/messages/bookingNumberChanged.json".readResourceAsText()

    awsSqsClient.sendMessage(dlqUrl, message)

    webTestClient.put()
      .uri("/queue-admin/queue-housekeeping")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { queueAdminService.getEventDlqMessageCount() } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/identifiers?type=MERGED") } matches { it == 2 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=false&extraInfo=true") } matches { it == 3 }
    await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A9999DY/nomsNumber") } matches { it == 1 }

    val processedMessage: Message? = messageRepository.findAll().firstOrNull()
    assertThat(processedMessage).isNotNull
    assertThat(processedMessage?.processedDate).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS))
    assertThat(processedMessage?.status).isEqualTo("COMPLETED")
  }

  @Test
  fun `will purge any messages on the dlq`() {
    awsSqsClient.sendMessage(dlqUrl, "{}")

    webTestClient.put()
      .uri("/queue-admin/purge-event-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_QUEUE_ADMIN")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { queueAdminService.getEventDlqMessageCount() } matches { it == 0 }
    assertThat(getNumberOfMessagesCurrentlyOnQueue()).isEqualTo(0)

    // Nothing to process
    prisonMockServer.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()))
    communityMockServer.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()))

    assertThat(messageRepository.findAll().firstOrNull()).isNull()
  }

  @Test
  fun `will consume a booking changed message on the dlq and return to main queue`() {
    val message = "/messages/bookingNumberChanged.json".readResourceAsText()

    awsSqsClient.sendMessage(dlqUrl, message)

    webTestClient.put()
      .uri("/queue-admin/transfer-event-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_QUEUE_ADMIN")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { queueAdminService.getEventDlqMessageCount() } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/identifiers?type=MERGED") } matches { it == 2 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=false&extraInfo=true") } matches { it == 3 }
    await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A9999DY/nomsNumber") } matches { it == 1 }

    val processedMessage: Message? = messageRepository.findAll().firstOrNull()
    assertThat(processedMessage).isNotNull
    assertThat(processedMessage?.processedDate).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS))
    assertThat(processedMessage?.status).isEqualTo("COMPLETED")
  }
}

private fun String.readResourceAsText(): String {
  return MessageIntegrationTest::class.java.getResource(this).readText()
}
