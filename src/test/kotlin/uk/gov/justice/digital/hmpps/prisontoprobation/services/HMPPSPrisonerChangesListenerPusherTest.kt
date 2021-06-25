package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HMPPSPrisonerChangesListenerPusherTest {
  private val communityService: CommunityService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val releaseAndRecallService = ReleaseAndRecallService(communityService, telemetryClient)
  private lateinit var pusher: HMPPSPrisonerChangesListenerPusher
  private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().apply {
    this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    this.registerModule(JavaTimeModule())
  }

  @BeforeEach
  fun before() {
    pusher = HMPPSPrisonerChangesListenerPusher(releaseAndRecallService, objectMapper)
  }

  @Nested
  inner class PrisonerReceivedEvents {

    @Test
    fun `will call community api for a prisoner received recall event`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerRecalled.json".readResourceAsText())
      verify(communityService).prisonerRecalled("A5194DY", "MDI", LocalDate.of(2020, 2, 12))
    }

    @Test
    fun `will not call community api for a prisoner received event we are not interested in`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReturnedFromCourt.json".readResourceAsText())
      verifyNoMoreInteractions(communityService)
    }
  }

  @Nested
  inner class PrisonerReleasedEvents {

    @Test
    fun `will call community api for a prisoner released event`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReleased.json".readResourceAsText())
      verify(communityService).prisonerReleased("A5194DY", LocalDate.of(2020, 2, 12))
    }

    @Test
    fun `will call community api for a prisoner released to hospital event`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReleasedToHospital.json".readResourceAsText())
      verify(communityService).prisonerReleased("A5194DY", LocalDate.of(2020, 2, 12))
    }

    @Test
    fun `will not call community api for a prisoner released event we are not interested in`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReleasedOnTemporaryAbsence.json".readResourceAsText())
      verifyNoMoreInteractions(communityService)
    }
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
