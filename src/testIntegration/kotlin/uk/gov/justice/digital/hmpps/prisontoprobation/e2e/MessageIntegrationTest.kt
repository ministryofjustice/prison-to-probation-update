package uk.gov.justice.digital.hmpps.prisontoprobation.e2e

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class MessageIntegrationTest : QueueListenerIntegrationTest() {

  @Test
  fun `will consume a prison movement message, update probation`() {
    val message = "/messages/externalMovement.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    prisonEventQueueSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/movement/1") } matches { it == 2 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=false&extraInfo=true") } matches { it == 3 }
    await untilCallTo { eliteRequestCountFor("/api/prisoners?offenderNo=A5089DY") } matches { it == 1 }
    await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber/38339A") } matches { it == 1 }

    val processedMessage: Message? = messageRepository.findAll().firstOrNull()
    assertThat(processedMessage).isNotNull
    assertThat(processedMessage?.processedDate).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS))
    assertThat(processedMessage?.status).isEqualTo("COMPLETED")
  }

  @Test
  fun `will consume a imprisonment status change message, update probation`() {
    val message = "/messages/imprisonmentStatusChanged.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    prisonEventQueueSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/sentenceDetail") } matches { it == 2 }
    await untilCallTo { eliteRequestCountFor("/api/offender-sentences/booking/1200835/sentenceTerms") } matches { it == 1 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=false&extraInfo=true") } matches { it == 3 }
    await untilCallTo { offenderSearchPostCountFor("/match") } matches { it == 1 }
    await untilCallTo { communityGetCountFor("/secure/offenders/crn/X142620/convictions") } matches { it == 1 }
    await untilCallTo { communityGetCountFor("/secure/offenders/crn/X181002/convictions") } matches { it == 1 }
    await untilCallTo { communityPutCountFor("/secure/offenders/crn/X142620/nomsNumber") } matches { it == 1 }
    await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber") } matches { it == 1 }
    await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber/38339A") } matches { it == 1 }
    await untilCallTo { communityPostCountFor("/secure/offenders/nomsNumber/A5089DY/bookingNumber/38339A/custody/keyDates") } matches { it == 1 }

    val processedMessage: Message? = messageRepository.findAll().firstOrNull()
    assertThat(processedMessage).isNotNull
    assertThat(processedMessage?.processedDate).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS))
    assertThat(processedMessage?.matchingCrns).isEqualTo("X142620")
    assertThat(processedMessage?.status).isEqualTo("COMPLETED")
  }

  @Test
  fun `will consume a booking changed message, update probation`() {
    val message = "/messages/bookingNumberChanged.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    prisonEventQueueSqsClient.sendMessage(queueUrl, message)

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
