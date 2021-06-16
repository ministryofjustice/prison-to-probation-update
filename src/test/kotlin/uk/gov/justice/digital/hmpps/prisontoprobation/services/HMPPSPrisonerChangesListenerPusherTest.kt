package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HMPPSPrisonerChangesListenerPusherTest {
  private val communityService: CommunityService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val externalMovementService = ExternalMovementService(communityService, telemetryClient)
  private lateinit var pusher: HMPPSPrisonerChangesListenerPusher

  @BeforeEach
  fun before() {
    pusher = HMPPSPrisonerChangesListenerPusher(externalMovementService)
  }

  @Test
  fun `will call community api for a prisoner received event`() {
    pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReceived.json".readResourceAsText())
    verify(communityService).prisonerReceived("A5194DY", PrisonerReceivedDetails("A5194DY", "RECALL", "PROBATION", "Recall referral date 2021-05-12"))
  }

  @Test
  fun `will call community api for a prisoner received event with minimum data`() {
    pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReceivedNoSourceOrDetails.json".readResourceAsText())
    verify(communityService).prisonerReceived("A5194DY", PrisonerReceivedDetails("A5194DY", "RECALL"))
  }

  @Test
  internal fun `will not call service for events we don't understand`() {
    pusher.pushHMPPSPrisonUpdateToProbation("/messages/imprisonmentStatusChanged.json".readResourceAsText())
    verifyNoMoreInteractions(communityService)
  }
}

private fun String.readResourceAsText(): String {
  return MessageProcessorTest::class.java.getResource(this).readText()
}
