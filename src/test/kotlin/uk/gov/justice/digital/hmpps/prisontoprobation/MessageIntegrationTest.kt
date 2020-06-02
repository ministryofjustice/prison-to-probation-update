package uk.gov.justice.digital.hmpps.prisontoprobation

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.internal.util.MockUtil
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.PropertySource
import org.springframework.test.annotation.DirtiesContext
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageProcessor
import javax.inject.Inject

@SpringBootTest(properties = [
  "prisontoprobation.message-processor.enabled=true",
  "prisontoprobation.message-processor.delay=50",
  "prisontoprobation.hold-back.duration=0m"
])
@DirtiesContext
class MessageIntegrationTest : QueueIntegrationTest() {
  @Inject
  private lateinit var messageProcessor: MessageProcessor
  @Inject
  private lateinit var messageRepository: MessageRepository

  @BeforeEach
  internal fun setUp() {
    messageRepository.deleteAll()
    println("We should have a real MessageProcessor it is $messageProcessor and Mockito thinks isMock=${MockUtil.isMock(messageProcessor)}")
  }

  @Test
  fun `will consume a prison movement message, update probation and create movement insights event`() {
    val message = "/messages/externalMovement.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/movement/1") } matches { it == 2 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=true") } matches { it == 2 }
    await untilCallTo { eliteRequestCountFor("/api/prisoners?offenderNo=A5089DY") } matches { it == 1 }
    await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber/38339A") } matches { it == 1 }
  }

  @Test
  fun `will consume a imprisonment status change message, update probation and create movement insights event`() {
    val message = "/messages/imprisonmentStatusChanged.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/sentenceDetail") } matches { it == 1 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=true") } matches { it == 1 }
    // this will be 2 while we have additional analysis code in since we do everything twice
    await untilCallTo { offenderSearchPostCountFor("/match") } matches { it == 2 }
    await untilCallTo { communityGetCountFor("/secure/offenders/crn/X142620/convictions") } matches { it == 2 }
    await untilCallTo { communityGetCountFor("/secure/offenders/crn/X181002/convictions") } matches { it == 2 }
    await untilCallTo { communityPutCountFor("/secure/offenders/crn/X142620/nomsNumber") } matches { it == 1 }
    await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber") } matches { it == 1 }
    await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber/38339A") } matches { it == 1 }
    await untilCallTo { communityPostCountFor("/secure/offenders/nomsNumber/A5089DY/bookingNumber/38339A/custody/keyDates") } matches { it == 1 }

  }

  @Test
  fun `will consume a sentence date change message, update probation and create movement insights event`() {
    val message = "/messages/sentenceDatesChanged.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=true") } matches { it == 1 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/sentenceDetail") } matches { it == 1 }
    await untilCallTo { communityPostCountFor("/secure/offenders/nomsNumber/A5089DY/bookingNumber/38339A/custody/keyDates") } matches { it == 1 }
  }

  @Test
  fun `will consume a confirmed release date change message, update probation`() {
    val message = "/messages/confirmedReleaseDateChanged.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=true") } matches { it == 1 }
    await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/sentenceDetail") } matches { it == 1 }
    await untilCallTo { communityPostCountFor("/secure/offenders/nomsNumber/A5089DY/bookingNumber/38339A/custody/keyDates") } matches { it == 1 }
  }

}

private fun String.readResourceAsText(): String {
  return MessageIntegrationTest::class.java.getResource(this).readText()
}