package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
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
    whenever(messageProcessor.validateMessage(any(), any())).thenReturn(RetryLater(99L))

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