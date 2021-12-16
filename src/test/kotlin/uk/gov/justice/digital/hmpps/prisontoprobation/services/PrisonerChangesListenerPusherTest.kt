package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PrisonerChangesListenerPusherTest {
  private val messageProcessor: MessageProcessor = mock()
  private val messageRetryService: MessageRetryService = mock()

  private lateinit var pusher: PrisonerChangesListenerPusher

  @BeforeEach
  fun before() {
    pusher = PrisonerChangesListenerPusher(messageProcessor, messageRetryService)
  }

  @Test
  fun `will schedule for processing when valid message that needs scheduling`() {
    whenever(messageProcessor.validateMessage(any(), any())).thenReturn(TryLater(99L))

    pusher.pushPrisonUpdateToProbation("/messages/imprisonmentStatusChanged.json".readResourceAsText())

    verify(messageRetryService).scheduleForProcessing(
      bookingId = eq(99L),
      eventType = eq("IMPRISONMENT_STATUS-CHANGED"),
      message = eq("{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"),
      eq(
        SynchroniseStatus(state = SynchroniseState.VALIDATED)
      )
    )
  }

  @Test
  fun `will not schedule for processing when message processing done`() {
    whenever(messageProcessor.validateMessage(any(), any())).thenReturn(Done())

    pusher.pushPrisonUpdateToProbation("/messages/imprisonmentStatusChanged.json".readResourceAsText())

    verify(messageRetryService, never()).scheduleForProcessing(any(), any(), any(), any())
  }
}

private fun String.readResourceAsText(): String {
  return MessageProcessorTest::class.java.getResource(this).readText()
}
