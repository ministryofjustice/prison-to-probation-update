package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrisonerChangesListenerPusherTest {
  private val prisonMovementService: PrisonMovementService = mock()
  private val bookingChangeService: BookingChangeService = mock()
  private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService = mock()
  private val sentenceDatesChangeService: SentenceDatesChangeService = mock()

  private lateinit var listener: PrisonerChangesListenerPusher

  @BeforeEach
  fun before() {
    listener = PrisonerChangesListenerPusher(prisonMovementService, bookingChangeService, imprisonmentStatusChangeService, sentenceDatesChangeService)
  }

  @Test
  fun `external prisoner movements will be checked for processing`() {
    // message body is as follows
    // "Message": "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}",
    listener.pushPrisonUpdateToProbation("/messages/externalMovement.json".readResourceAsText())

    verify(prisonMovementService).checkMovementAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200835)
      assertThat(it.movementSeq).isEqualTo(1)
    })
  }

  @Test
  fun `imprisonment status change will be checked for processing`() {
    // message body is as follows
    // "Message": "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
    listener.pushPrisonUpdateToProbation("/messages/imprisonmentStatusChanged.json".readResourceAsText())

    verify(imprisonmentStatusChangeService).checkImprisonmentStatusChangeAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200835L)
    })
  }

  @Test
  fun `sentence date change will be checked for processing`() {
    // message body is as follows
    //   "Message": "{\"eventType\":\"SENTENCE_DATES-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"bookingId\":1200849,\"sentenceCalculationId\":5628783,\"nomisEventType\":\"S2_RESULT\"}",
    listener.pushPrisonUpdateToProbation("/messages/sentenceDatesChanged.json".readResourceAsText())

    verify(sentenceDatesChangeService).checkSentenceDateChangeAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200835L)
    })
  }

  @Test
  fun `other messages are ignored`() {
    listener.pushPrisonUpdateToProbation("/messages/notAnExternalMovement.json".readResourceAsText())

    verify(prisonMovementService, never()).checkMovementAndUpdateProbation(any())
    verify(imprisonmentStatusChangeService, never()).checkImprisonmentStatusChangeAndUpdateProbation(any())
    verify(sentenceDatesChangeService, never()).checkSentenceDateChangeAndUpdateProbation(any())
    verify(bookingChangeService, never()).checkBookingNumberChangedAndUpdateProbation(any())
  }

}

private fun String.readResourceAsText(): String {
  return PrisonerChangesListenerPusherTest::class.java.getResource(this).readText()
}