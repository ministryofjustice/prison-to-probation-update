package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class PrisonerMovementListenerPusherTest {
  private val prisonMovementService: PrisonMovementService = mock()
  private val bookingChangeService: BookingChangeService = mock()
  private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService = mock()

  private lateinit var listener: PrisonerMovementListenerPusher

  @Before
  fun before() {
    listener = PrisonerMovementListenerPusher(prisonMovementService, bookingChangeService, imprisonmentStatusChangeService)
  }

  @Test
  fun `external prisoner movements will be checked for processing`() {
    // message body is as follows
    // "Message": "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}",
    listener.pushPrisonMovementToProbation("/messages/externalMovement.json".readResourceAsText())

    verify(prisonMovementService).checkMovementAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200835)
      assertThat(it.movementSeq).isEqualTo(1)
    })
  }

  @Test
  fun `imprisonment status change will be checked for processing`() {
    // message body is as follows
    // "Message": "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200795,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
    listener.pushPrisonMovementToProbation("/messages/imprisonmentStatusChanged.json".readResourceAsText())

    verify(imprisonmentStatusChangeService).checkImprisonmentStatusChangeAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200795)
    })
  }

  @Test
  fun `sentence imposed change will be checked for processing`() {
    // message body is as follows
    //  "Message": "{\"eventId\":\"7464509\",\"eventType\":\"SENTENCE-IMPOSED\",\"eventDatetime\":\"2020-02-12T15:14:26.706918\",\"rootOffenderId\":2581714,\"offenderIdDisplay\":\"A5081DY\",\"caseNoteId\":47006377,\"agencyLocationId\":\"MDI\"}",
    listener.pushPrisonMovementToProbation("/messages/sentenceImposed.json".readResourceAsText())

    verify(imprisonmentStatusChangeService).checkSentenceImposedAndUpdateProbation(check {
      assertThat(it.offenderNo).isEqualTo("A5081DY")
    })
  }

  @Test
  fun `other messages are ignored`() {
    listener.pushPrisonMovementToProbation("/messages/notAnExternalMovement.json".readResourceAsText())

    verify(prisonMovementService, never()).checkMovementAndUpdateProbation(any())
    verify(imprisonmentStatusChangeService, never()).checkImprisonmentStatusChangeAndUpdateProbation(any())
    verify(imprisonmentStatusChangeService, never()).checkSentenceImposedAndUpdateProbation(any())
    verify(bookingChangeService, never()).checkBookingReassignedAndUpdateProbation(any())
    verify(bookingChangeService, never()).checkBookingCreationAndUpdateProbation(any())
    verify(bookingChangeService, never()).checkBookingNumberChangedAndUpdateProbation(any())
  }

}

private fun String.readResourceAsText(): String {
  return PrisonerMovementListenerPusherTest::class.java.getResource(this).readText()
}