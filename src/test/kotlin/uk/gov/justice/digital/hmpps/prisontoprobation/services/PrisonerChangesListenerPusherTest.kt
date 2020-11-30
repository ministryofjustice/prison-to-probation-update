package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrisonerChangesListenerPusherTest {
  private val messageProcessor: MessageProcessor = mock()
  private val messageRetryService: MessageRetryService = mock()

  private lateinit var pusher: PrisonerChangesListenerPusher

  @BeforeEach
  fun before() {
    pusher = PrisonerChangesListenerPusher(messageProcessor, messageRetryService)
  }

  @Test
  fun `will call retry service when requested`() {
    whenever(messageProcessor.validateMessage(any(), any())).thenReturn(TryLater(99L))

    pusher.pushPrisonUpdateToProbation("/messages/imprisonmentStatusChanged.json".readResourceAsText())

    verify(messageRetryService).scheduleForProcessing(bookingId = eq(99L), eventType = eq("IMPRISONMENT_STATUS-CHANGED"), message = eq("{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"))
  }

  @Test
  fun `will not call retry service when done`() {
    whenever(messageProcessor.validateMessage(any(), any())).thenReturn(Done())

    pusher.pushPrisonUpdateToProbation("/messages/imprisonmentStatusChanged.json".readResourceAsText())

    verify(messageRetryService, never()).retryLater(any(), any(), any())
  }
}

private fun String.readResourceAsText(): String {
  return MessageProcessorTest::class.java.getResource(this).readText()
}
