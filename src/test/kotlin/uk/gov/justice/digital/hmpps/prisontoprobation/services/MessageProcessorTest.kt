package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDate

class MessageProcessorTest {
  private val prisonMovementService: PrisonMovementService = mock()
  private val bookingChangeService: BookingChangeService = mock()
  private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService = mock()
  private val retryableEventMetricsService: RetryableEventMetricsService = mock()

  private lateinit var messageProcessor: MessageProcessor

  @BeforeEach
  fun before() {
    messageProcessor = MessageProcessor(
      prisonMovementService,
      bookingChangeService,
      imprisonmentStatusChangeService,
      retryableEventMetricsService,
    )
  }

  @Test
  fun `external prisoner movements will be checked for processing`() {
    messageProcessor.processMessage(
      aMessage(
        "EXTERNAL_MOVEMENT_RECORD-INSERTED",
        "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}",
      ),
    )

    verify(prisonMovementService).processMovementAndUpdateProbation(
      check {
        assertThat(it.bookingId).isEqualTo(1200835)
        assertThat(it.movementSeq).isEqualTo(1)
      },
    )
  }

  @Test
  fun `imprisonment status change will be checked for processing`() {
    messageProcessor.processMessage(
      aMessage(
        "IMPRISONMENT_STATUS-CHANGED",
        "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
      ),
    )

    verify(imprisonmentStatusChangeService).processImprisonmentStatusChangeAndUpdateProbation(
      check {
        assertThat(it.bookingId).isEqualTo(1200835L)
      },
    )
  }

  @Test
  fun `other messages are ignored`() {
    messageProcessor.processMessage(aMessage("SOME_OTHER_MESSAGE", "{\"eventType\":\"SOME_OTHER_MESSAGE\"}"))

    verify(prisonMovementService, never()).processMovementAndUpdateProbation(any())
    verify(imprisonmentStatusChangeService, never()).processImprisonmentStatusChangeAndUpdateProbation(any())
    verify(bookingChangeService, never()).processBookingNumberChangedAndUpdateProbation(any())
  }

  @Test
  fun `successful events call the metric service`() {
    whenever(prisonMovementService.processMovementAndUpdateProbation(any())).thenReturn(Done())

    messageProcessor.processMessage(
      aMessage(
        "EXTERNAL_MOVEMENT_RECORD-INSERTED",
        "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}",
      ),
    )

    verify(retryableEventMetricsService).eventSucceeded(anyString(), any(), anyInt())
  }

  @Test
  fun `failed events call the metric service`() {
    whenever(prisonMovementService.processMovementAndUpdateProbation(any())).thenReturn(
      TryLater(
        1200835,
        LocalDate.now().plusDays(1),
      ),
    )

    messageProcessor.processMessage(
      aMessage(
        "EXTERNAL_MOVEMENT_RECORD-INSERTED",
        "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}",
      ),
    )

    verify(retryableEventMetricsService).eventFailed(anyString(), any())
  }

  private fun aMessage(eventType: String, message: String) =
    Message(eventType = eventType, message = message)
}
