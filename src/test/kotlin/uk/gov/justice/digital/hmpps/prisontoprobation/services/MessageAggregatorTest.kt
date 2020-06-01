package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.nhaarman.mockitokotlin2.reset
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
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
@TestPropertySource(properties = [
  "prisontoprobation.hold-back.duration=10m"
])
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
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(Done())
    Assertions.assertThat(repository.findAll()).isEmpty()
  }


  @Test
  fun `will do nothing when no messages need processing`() {
    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, never()).processMessage(any(), any())
  }

  @Test
  fun `will do nothing when no "first time" messages need processing`() {
    repository.save(Message(bookingId = 99L, retryCount = 1, createdDate = LocalDateTime.now(), eventType = "IMPRISONMENT_STATUS-CHANGED", message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, never()).processMessage(any(), any())
  }

  @Test
  fun `will do nothing when no recent "first time" messages need processing`() {
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "IMPRISONMENT_STATUS-CHANGED", message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, never()).processMessage(any(), any())
  }

  @Test
  fun `will process when a single old "first time" messages need processing`() {
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "IMPRISONMENT_STATUS-CHANGED", message = message))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor, times(1)).processMessage("IMPRISONMENT_STATUS-CHANGED", message)
  }

  @Test
  fun `will process messages in date order`() {
    val tooYoungMessage = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":99,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(1), eventType = "IMPRISONMENT_STATUS-CHANGED", message = tooYoungMessage))
    val youngMessage = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":100,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 100L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "IMPRISONMENT_STATUS-CHANGED", message = youngMessage))
    val veryOldMessage = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":999,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 999L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(99), eventType = "IMPRISONMENT_STATUS-CHANGED", message = veryOldMessage))
    val oldMessage = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":101,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 101L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(20), eventType = "IMPRISONMENT_STATUS-CHANGED", message = oldMessage))

    messageAggregator.processMessagesForNextBookingSets()

    val orderVerifier = inOrder(messageProcessor)
    orderVerifier.verify(messageProcessor).processMessage("IMPRISONMENT_STATUS-CHANGED", veryOldMessage)
    orderVerifier.verify(messageProcessor).processMessage("IMPRISONMENT_STATUS-CHANGED", oldMessage)
    orderVerifier.verify(messageProcessor).processMessage("IMPRISONMENT_STATUS-CHANGED", youngMessage)
    orderVerifier.verify(messageProcessor, never()).processMessage("IMPRISONMENT_STATUS-CHANGED", tooYoungMessage)
  }

  @Test
  fun `will process messages in date order grouped by booking`() {
    val youngSentenceChangeMessage = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":999,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 999L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(1), eventType = "SENTENCE_DATES-CHANGED", message = youngSentenceChangeMessage))
    val tooYoungMessage = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":99,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(1), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = tooYoungMessage))
    val youngMessage = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":100,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 100L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = youngMessage))
    val veryOldMessage = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":999,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 999L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(99), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = veryOldMessage))
    val oldMessage = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":101,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    repository.save(Message(bookingId = 101L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(20), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = oldMessage))

    messageAggregator.processMessagesForNextBookingSets()

    val orderVerifier = inOrder(messageProcessor)
    orderVerifier.verify(messageProcessor).processMessage("EXTERNAL_MOVEMENT_RECORD-INSERTED", veryOldMessage)
    orderVerifier.verify(messageProcessor).processMessage("SENTENCE_DATES-CHANGED", youngSentenceChangeMessage)
    orderVerifier.verify(messageProcessor).processMessage("EXTERNAL_MOVEMENT_RECORD-INSERTED", oldMessage)
    orderVerifier.verify(messageProcessor).processMessage("EXTERNAL_MOVEMENT_RECORD-INSERTED", youngMessage)
    orderVerifier.verify(messageProcessor, never()).processMessage("EXTERNAL_MOVEMENT_RECORD-INSERTED", tooYoungMessage)
  }

  @Test
  fun `will process all other messages for a booking when old "first time" messages need processing regardless of age`() {
    val prisonLocationChangeMessage = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":99,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"
    val sentenceDateChangeMessage = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}"
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = prisonLocationChangeMessage))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor).processMessage("EXTERNAL_MOVEMENT_RECORD-INSERTED", prisonLocationChangeMessage)
    verify(messageProcessor).processMessage("SENTENCE_DATES-CHANGED", sentenceDateChangeMessage)
  }

  @Test
  fun `will de-duplicate the messages prior to processing`() {
    val prisonLocationChangeMessage = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":99,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"
    val sentenceDateChangeMessage1 = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}"
    val sentenceDateChangeMessage2 = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:33.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628784,\"nomisEventType\":\"S2_RESULT\"}"
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = prisonLocationChangeMessage))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage1))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage2))

    messageAggregator.processMessagesForNextBookingSets()

    verify(messageProcessor).processMessage("EXTERNAL_MOVEMENT_RECORD-INSERTED", prisonLocationChangeMessage)
    verify(messageProcessor, times(1)).processMessage(eq("SENTENCE_DATES-CHANGED"), any())
  }

  @Test
  fun `will only process message once each is successfully processed`() {
    val prisonLocationChangeMessage = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":99,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"
    val sentenceDateChangeMessage1 = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}"
    val sentenceDateChangeMessage2 = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:33.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628784,\"nomisEventType\":\"S2_RESULT\"}"
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = prisonLocationChangeMessage))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage1))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = sentenceDateChangeMessage2))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any(), any())
    reset(messageProcessor)

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, never()).processMessage(any(), any())
  }

  @Test
  fun `no messages will be retried if all successful`() {
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":99,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}"))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any(), any())
    reset(messageProcessor)

    retryService.retryShortTerm()
    retryService.retryMediumTerm()
    retryService.retryLongTerm()

    verify(messageProcessor, never()).processMessage(any(), any())
  }

  @Test
  fun `all messages will be retried if all fail with unexpected exceptions`() {
    whenever(messageProcessor.processMessage(any(), any())).thenThrow(RuntimeException("oops"))

    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":99,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}"))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any(), any())
    reset(messageProcessor)

    retryService.retryShortTerm()
    verify(messageProcessor, times(2)).processMessage(any(), any())
  }

  @Test
  fun `some messages will be retried if some fail with unexpected exceptions`() {
    whenever(messageProcessor.processMessage(any(), any()))
        .thenThrow(RuntimeException("oops"))
        .thenReturn(Done())

    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":99,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}"))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any(), any())
    reset(messageProcessor)

    retryService.retryShortTerm()
    verify(messageProcessor, times(1)).processMessage(any(), any())
  }

  @Test
  fun `all messages will be retried if all require to be retried`() {
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(RetryLater(99L))

    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now().minusMinutes(11), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":99,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"))
    repository.save(Message(bookingId = 99L, retryCount = 0, createdDate = LocalDateTime.now(), eventType = "SENTENCE_DATES-CHANGED", message = "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":99,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}"))

    messageAggregator.processMessagesForNextBookingSets()
    verify(messageProcessor, times(2)).processMessage(any(), any())
    reset(messageProcessor)

    retryService.retryShortTerm()
    verify(messageProcessor, times(2)).processMessage(any(), any())
  }

}