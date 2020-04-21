package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MessageProcessorTest {
  private val prisonMovementService: PrisonMovementService = mock()
  private val bookingChangeService: BookingChangeService = mock()
  private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService = mock()
  private val sentenceDatesChangeService: SentenceDatesChangeService = mock()

  private lateinit var messageProcessor: MessageProcessor

  @BeforeEach
  fun before() {
    messageProcessor = MessageProcessor(prisonMovementService, bookingChangeService, imprisonmentStatusChangeService, sentenceDatesChangeService)
  }

  @Test
  fun `external prisoner movements will be checked for processing`() {
    messageProcessor.processMessage("EXTERNAL_MOVEMENT_RECORD-INSERTED", "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}")

    verify(prisonMovementService).checkMovementAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200835)
      assertThat(it.movementSeq).isEqualTo(1)
    })
  }

  @Test
  fun `imprisonment status change will be checked for processing`() {
    messageProcessor.processMessage("IMPRISONMENT_STATUS-CHANGED", "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}")

    verify(imprisonmentStatusChangeService).checkImprisonmentStatusChangeAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200835L)
    })
  }

  @Test
  fun `sentence date change will be checked for processing`() {
    messageProcessor.processMessage("SENTENCE_DATES-CHANGED", "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":1200835,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}")

    verify(sentenceDatesChangeService).checkSentenceDateChangeAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200835L)
    })
  }

  @Test
  fun `other messages are ignored`() {
    messageProcessor.processMessage("SOME_OTHER_MESSAGE", "{\"eventType\":\"SOME_OTHER_MESSAGE\"}")

    verify(prisonMovementService, never()).checkMovementAndUpdateProbation(any())
    verify(imprisonmentStatusChangeService, never()).checkImprisonmentStatusChangeAndUpdateProbation(any())
    verify(sentenceDatesChangeService, never()).checkSentenceDateChangeAndUpdateProbation(any())
    verify(bookingChangeService, never()).checkBookingNumberChangedAndUpdateProbation(any())
  }

}
