package uk.gov.justice.digital.hmpps.prisontoprobation.e2e

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class HouseKeepingIntegrationTest : QueueListenerIntegrationTest() {

  @Test
  fun `housekeeping will consume a booking changed message on the dlq and return to main queue`() {
    val message = "/messages/bookingNumberChanged.json".readResourceAsText()

    prisonEventQueueSqsClient.sendMessage(SendMessageRequest.builder().queueUrl(dlqUrl).messageBody(message).build())

    webTestClient.put()
      .uri("/queue-admin/retry-all-dlqs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 0 }
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
    prisonEventQueueSqsClient.sendMessage(SendMessageRequest.builder().queueUrl(dlqUrl).messageBody("{}").build())

    webTestClient.put()
      .uri("/queue-admin/purge-queue/${prisonEventQueue.dlqName}")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_QUEUE_ADMIN")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 0 }

    // Nothing to process
    prisonMockServer.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()))
    communityMockServer.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()))

    assertThat(messageRepository.findAll().firstOrNull()).isNull()
  }

  @Test
  fun `will consume a booking changed message on the dlq and return to main queue`() {
    val message = "/messages/bookingNumberChanged.json".readResourceAsText()

    prisonEventQueueSqsClient.sendMessage(SendMessageRequest.builder().queueUrl(dlqUrl).messageBody(message).build())

    webTestClient.put()
      .uri("/queue-admin/retry-dlq/${prisonEventQueue.dlqName}")
      .headers(setAuthorisation(roles = listOf("ROLE_PTPU_QUEUE_ADMIN")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 0 }
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
  return HouseKeepingIntegrationTest::class.java.getResource(this).readText()
}
