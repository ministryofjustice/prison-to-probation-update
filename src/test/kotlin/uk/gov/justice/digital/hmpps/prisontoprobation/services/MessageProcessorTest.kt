package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message

class MessageProcessorTest {
  private val bookingChangeService: BookingChangeService = mock()
  private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService = mock()
  private val retryableEventMetricsService: RetryableEventMetricsService = mock()

  private lateinit var messageProcessor: MessageProcessor

  @BeforeEach
  fun before() {
    messageProcessor = MessageProcessor(
      bookingChangeService,
      imprisonmentStatusChangeService,
      retryableEventMetricsService,
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

  private fun aMessage(eventType: String, message: String) =
    Message(eventType = eventType, message = message)
}
