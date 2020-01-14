package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.mock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrisonerMovementListenerPusherTest {
  private val offenderService: OffenderService = mock()
  private val communityService: CommunityService = mock()

  private lateinit var listener: PrisonerMovementListenerPusher

  @Before
  fun before() {
    listener = PrisonerMovementListenerPusher(offenderService, communityService)
  }

  @Test
  fun `does nothing`() {
    val message = this::class.java.getResource("/messages/externalMovement.json").readText()

    listener.pushPrisonMovementToProbation(message)
  }

}
