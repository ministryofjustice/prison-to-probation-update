package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class PrisonerMovementListenerPusherTest {
  private val prisonMovementService: PrisonMovementService = mock()
  private val bookingChangeService: BookingChangeService = mock()
  private val sentenceChangeService: SentenceChangeService = mock()

  private lateinit var listener: PrisonerMovementListenerPusher

  @Before
  fun before() {
    listener = PrisonerMovementListenerPusher(prisonMovementService, bookingChangeService, sentenceChangeService)
  }

  @Test
  fun `external prisoner movements will be checked for processing`() {
    val message = this::class.java.getResource("/messages/externalMovement.json").readText()
    // message body is as follows
    // "Message": "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}",

    listener.pushPrisonMovementToProbation(message)

    verify(prisonMovementService).checkMovementAndUpdateProbation(check {
      assertThat(it.bookingId).isEqualTo(1200835)
      assertThat(it.movementSeq).isEqualTo(1)
    })
  }

  @Test
  fun `non movement messages are ignored`() {
    val message = this::class.java.getResource("/messages/notAnExternalMovement.json").readText()

    listener.pushPrisonMovementToProbation(message)

    verify(prisonMovementService, never()).checkMovementAndUpdateProbation(any())
  }

}
