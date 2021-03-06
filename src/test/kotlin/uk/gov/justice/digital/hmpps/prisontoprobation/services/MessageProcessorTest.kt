package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDate

class MessageProcessorTest {
  private val prisonMovementService: PrisonMovementService = mock()
  private val bookingChangeService: BookingChangeService = mock()
  private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService = mock()
  private val sentenceDatesChangeService: SentenceDatesChangeService = mock()
  private val retryableEventMetricsService: RetryableEventMetricsService = mock()

  private lateinit var messageProcessor: MessageProcessor

  @BeforeEach
  fun before() {
    messageProcessor = MessageProcessor(
      prisonMovementService,
      bookingChangeService,
      imprisonmentStatusChangeService,
      sentenceDatesChangeService,
      retryableEventMetricsService,
    )
  }

  @Test
  fun `external prisoner movements will be checked for processing`() {
    messageProcessor.processMessage(
      aMessage(
        "EXTERNAL_MOVEMENT_RECORD-INSERTED",
        "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"
      )
    )

    verify(prisonMovementService).processMovementAndUpdateProbation(
      check {
        assertThat(it.bookingId).isEqualTo(1200835)
        assertThat(it.movementSeq).isEqualTo(1)
      }
    )
  }

  @Test
  fun `imprisonment status change will be checked for processing`() {
    messageProcessor.processMessage(
      aMessage(
        "IMPRISONMENT_STATUS-CHANGED",
        "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
      )
    )

    verify(imprisonmentStatusChangeService).processImprisonmentStatusChangeAndUpdateProbation(
      check {
        assertThat(it.bookingId).isEqualTo(1200835L)
      }
    )
  }

  @Test
  fun `sentence date change will be checked for processing`() {
    messageProcessor.processMessage(
      aMessage(
        "SENTENCE_DATES-CHANGED",
        "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":1200835,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}"
      )
    )

    verify(sentenceDatesChangeService).processSentenceDateChangeAndUpdateProbation(
      check {
        assertThat(it.bookingId).isEqualTo(1200835L)
      }
    )
  }

  @Test
  fun `confirmed release date change will be checked for processing`() {
    messageProcessor.processMessage(
      aMessage(
        "CONFIRMED_RELEASE_DATE-CHANGED",
        "{\"eventType\":\"CONFIRMED_RELEASE_DATE-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":1200835,\"nomisEventType\":\"CONFIRMED_RELEASE_DATE-CHANGED\"}"
      )
    )

    verify(sentenceDatesChangeService).processSentenceDateChangeAndUpdateProbation(
      check {
        assertThat(it.bookingId).isEqualTo(1200835L)
      }
    )
  }

  @Test
  fun `other messages are ignored`() {
    messageProcessor.processMessage(aMessage("SOME_OTHER_MESSAGE", "{\"eventType\":\"SOME_OTHER_MESSAGE\"}"))

    verify(prisonMovementService, never()).processMovementAndUpdateProbation(any())
    verify(imprisonmentStatusChangeService, never()).processImprisonmentStatusChangeAndUpdateProbation(any())
    verify(sentenceDatesChangeService, never()).processSentenceDateChangeAndUpdateProbation(any())
    verify(bookingChangeService, never()).processBookingNumberChangedAndUpdateProbation(any())
  }

  @Test
  fun `successful events call the metric service`() {
    whenever(prisonMovementService.processMovementAndUpdateProbation(any())).thenReturn(Done())

    messageProcessor.processMessage(
      aMessage(
        "EXTERNAL_MOVEMENT_RECORD-INSERTED",
        "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"
      )
    )

    verify(retryableEventMetricsService).eventSucceeded(anyString(), any(), anyInt())
  }

  @Test
  fun `failed events call the metric service`() {
    whenever(prisonMovementService.processMovementAndUpdateProbation(any())).thenReturn(
      TryLater(
        1200835,
        LocalDate.now().plusDays(1)
      )
    )

    messageProcessor.processMessage(
      aMessage(
        "EXTERNAL_MOVEMENT_RECORD-INSERTED",
        "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"
      )
    )

    verify(retryableEventMetricsService).eventFailed(anyString(), any())
  }

  private fun aMessage(eventType: String, message: String) =
    Message(eventType = eventType, message = message)
}
