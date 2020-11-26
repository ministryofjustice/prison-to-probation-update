package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.nhaarman.mockitokotlin2.reset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
  properties = [
    "prisontoprobation.hold-back.duration=10m"
  ]
)
internal class MessageAggregatorTest {
  @Autowired
  private lateinit var repository: MessageRepository

  @Autowired
  private lateinit var messageAggregator: MessageAggregator

  @Autowired
  private lateinit var retryService: MessageRetryService

  @MockBean
  private lateinit var messageProcessor: MessageProcessor

  @BeforeEach
  fun setup() {
    repository.deleteAll()
    whenever(messageProcessor.processMessage(any())).thenReturn(Done())
  }

  @Test
  fun `will do nothing when no messages need processing`() {
    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, never()).processMessage(any())
  }

  @Test
  fun `will do nothing when no first-time messages need processing`() {
    repository.save(Message(bookingId = 99, retryCount = 1, createdDate = LocalDateTime.now(), eventType = "IMPRISONMENT_STATUS-CHANGED", message = imprisonmentStatusChangedMessage(1200835)))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, never()).processMessage(any())
  }

  @Test
  fun `will do nothing when no recent first-time messages need processing`() {
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "IMPRISONMENT_STATUS-CHANGED", message = imprisonmentStatusChangedMessage(1200835)))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, never()).processMessage(any())
  }

  @Test
  fun `will process when a single old first-time messages need processing`() {
    val message = imprisonmentStatusChangedMessage(1200835)
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "IMPRISONMENT_STATUS-CHANGED", message = message))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, times(1)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo("IMPRISONMENT_STATUS-CHANGED")
        assertThat(it.message).isEqualTo(message)
      }
    )
  }

  @Test
  fun `will process messages in any order for each booking batch`() {
    val tooYoungMessage = imprisonmentStatusChangedMessage(99)
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(1), eventType = "IMPRISONMENT_STATUS-CHANGED", message = tooYoungMessage))
    val youngMessage = imprisonmentStatusChangedMessage(100)
    repository.save(Message(bookingId = 100L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "IMPRISONMENT_STATUS-CHANGED", message = youngMessage))
    val veryOldMessage = imprisonmentStatusChangedMessage(999)
    repository.save(Message(bookingId = 999, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(99), eventType = "IMPRISONMENT_STATUS-CHANGED", message = veryOldMessage))
    val oldMessage = imprisonmentStatusChangedMessage(101)
    repository.save(Message(bookingId = 101L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(20), eventType = "IMPRISONMENT_STATUS-CHANGED", message = oldMessage))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, times(3)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo("IMPRISONMENT_STATUS-CHANGED")
      }
    )
  }

  @Test
  fun `will process messages in date order within group of bookings`() {
    val youngSentenceChangeMessage = sentenceDatesChangedMessage(999)
    repository.save(Message(bookingId = 999, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(1), eventType = "SENTENCE_DATES-CHANGED", message = youngSentenceChangeMessage))
    val tooYoungMessage = externalMovementInsertedMessage(99)
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(1), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = tooYoungMessage))
    val youngMessage = externalMovementInsertedMessage(100)
    repository.save(Message(bookingId = 100L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = youngMessage))
    val veryOldMessage = externalMovementInsertedMessage(999)
    repository.save(Message(bookingId = 999, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(99), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = veryOldMessage))
    val oldMessage = externalMovementInsertedMessage(101)
    repository.save(Message(bookingId = 101L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(20), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = oldMessage))

    messageAggregator.processMessagesForNextBookingSets()

    val orderVerifier = inOrder(messageProcessor)
    orderVerifier.verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "EXTERNAL_MOVEMENT_RECORD-INSERTED" &&
          this.message == veryOldMessage
      }
    )
    orderVerifier.verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "SENTENCE_DATES-CHANGED" &&
          this.message == youngSentenceChangeMessage
      }
    )
    verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "EXTERNAL_MOVEMENT_RECORD-INSERTED" &&
          this.message == oldMessage
      }
    )
    verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "EXTERNAL_MOVEMENT_RECORD-INSERTED" &&
          this.message == youngMessage
      }
    )
    verify(messageProcessor, never()).processMessage(
      argThat {
        this.eventType == "EXTERNAL_MOVEMENT_RECORD-INSERTED" &&
          this.message == tooYoungMessage
      }
    )
  }

  @Test
  fun `will process messages even if they are in a retry state in date order within group of bookings`() {
    val sentenceChangeMessage = sentenceDatesChangedMessage(999)
    repository.save(Message(bookingId = 999, retryCount = 9, createdDate = LocalDateTime.now().minusDays(1), eventType = "SENTENCE_DATES-CHANGED", message = sentenceChangeMessage))
    val externalMovementMessage = externalMovementInsertedMessage(999)
    repository.save(Message(bookingId = 999, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(99), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = externalMovementMessage))

    messageAggregator.processMessagesForNextBookingSets()

    val orderVerifier = inOrder(messageProcessor)
    orderVerifier.verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "EXTERNAL_MOVEMENT_RECORD-INSERTED" &&
          this.message == externalMovementMessage
      }
    )
    orderVerifier.verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "SENTENCE_DATES-CHANGED" &&
          this.message == sentenceChangeMessage
      }
    )
  }

  @Test
  fun `will process all other messages for a booking when old first-time messages need processing regardless of age`() {
    val prisonLocationChangeMessage = externalMovementInsertedMessage(99)
    val sentenceDateChangeMessage = sentenceDatesChangedMessage(99)
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = prisonLocationChangeMessage))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage))

    messageAggregator.processMessagesForNextBookingSets()

    val orderVerifier = inOrder(messageProcessor)
    orderVerifier.verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "EXTERNAL_MOVEMENT_RECORD-INSERTED" &&
          this.message == prisonLocationChangeMessage
      }
    )
    orderVerifier.verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "SENTENCE_DATES-CHANGED" &&
          this.message == sentenceDateChangeMessage
      }
    )
  }

  @Test
  fun `will de-duplicate the messages prior to processing`() {
    val prisonLocationChangeMessage = externalMovementInsertedMessage(99)
    val sentenceDateChangeMessage1 = sentenceDatesChangedMessage(99, "2020-02-25T11:24:32.935401")
    val sentenceDateChangeMessage2 = sentenceDatesChangedMessage(99, "2020-02-25T11:24:33.935401")
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = prisonLocationChangeMessage))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage1))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage2))

    messageAggregator.processMessagesForNextBookingSets()

    val orderVerifier = inOrder(messageProcessor)
    orderVerifier.verify(messageProcessor).processMessage(
      argThat {
        this.eventType == "EXTERNAL_MOVEMENT_RECORD-INSERTED" &&
          this.message == prisonLocationChangeMessage
      }
    )
    orderVerifier.verify(messageProcessor, times(1)).processMessage(
      argThat {
        this.eventType == "SENTENCE_DATES-CHANGED"
      }
    )
  }

  @Test
  fun `will de-duplicate old messages prior to processing`() {
    val sentenceDateChangeMessage1 = sentenceDatesChangedMessage(99, "2020-02-25T11:24:32.935401")
    val sentenceDateChangeMessage2 = sentenceDatesChangedMessage(99, "2020-02-25T11:24:33.935401")
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage1))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage2))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, times(1)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo("SENTENCE_DATES-CHANGED")
      }
    )
  }

  @Test
  fun `will process the latest of duplicated old messages prior to processing`() {
    val oldestPrisonLocationChangeMessage = externalMovementInsertedMessage(99, 1)
    val latestPrisonLocationChangeMessage = externalMovementInsertedMessage(99, 3)
    val middlePrisonLocationChangeMessage = externalMovementInsertedMessage(99, 2)

    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(15), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = oldestPrisonLocationChangeMessage))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(13), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = latestPrisonLocationChangeMessage))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(14), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = middlePrisonLocationChangeMessage))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, times(1)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo("EXTERNAL_MOVEMENT_RECORD-INSERTED")
        assertThat(it.message).isEqualTo(latestPrisonLocationChangeMessage)
      }
    )
  }

  @Test
  fun `will only process message once each is successfully processed`() {
    val prisonLocationChangeMessage = externalMovementInsertedMessage(99)
    val sentenceDateChangeMessage1 = sentenceDatesChangedMessage(99)
    val sentenceDateChangeMessage2 = sentenceDatesChangedMessage(99)
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = prisonLocationChangeMessage))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage1))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage2))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any())
    reset(messageProcessor)

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, never()).processMessage(any())
  }

  @Test
  fun `no messages will be retried if all successful`() {
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = externalMovementInsertedMessage(99)))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDatesChangedMessage(99)))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any())
    reset(messageProcessor)

    retryService.retryShortTerm()
    retryService.retryMediumTerm()
    retryService.retryLongTerm()

    verify(messageProcessor, never()).processMessage(any())
  }

  @Test
  fun `all messages will be retried if all fail with unexpected exceptions`() {
    whenever(messageProcessor.processMessage(any())).thenThrow(RuntimeException("oops"))

    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = externalMovementInsertedMessage(99)))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDatesChangedMessage(99)))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any())
    reset(messageProcessor)

    retryService.retryShortTerm()
    verify(messageProcessor, times(2)).processMessage(any())
  }

  @Test
  fun `some messages will be retried if some fail with unexpected exceptions`() {
    whenever(messageProcessor.processMessage(any()))
      .thenThrow(RuntimeException("oops"))
      .thenReturn(Done())

    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = externalMovementInsertedMessage(99)))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDatesChangedMessage(99)))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any())
    reset(messageProcessor)

    retryService.retryShortTerm()
    verify(messageProcessor, times(1)).processMessage(any())
  }

  @Test
  fun `all messages will be retried if all require to be retried`() {
    whenever(messageProcessor.processMessage(any())).thenReturn(TryLater(99))

    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = externalMovementInsertedMessage(99)))
    repository.save(Message(bookingId = 99, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDatesChangedMessage(99)))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any())
    reset(messageProcessor)

    retryService.retryShortTerm()
    verify(messageProcessor, times(2)).processMessage(any())
  }

  private fun sentenceDatesChangedMessage(bookingId: Long, eventDateTime: String = "2020-02-12T15:14:24.125533"): String =
    "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"$eventDateTime\",\"bookingId\":$bookingId,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"

  private fun externalMovementInsertedMessage(bookingId: Long, movementSeq: Int = 1): String =
    "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":$bookingId,\"movementSeq\":$movementSeq,\"nomisEventType\":\"M1_RESULT\"}"

  private fun imprisonmentStatusChangedMessage(bookingId: Long): String =
    "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":$bookingId,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
}
