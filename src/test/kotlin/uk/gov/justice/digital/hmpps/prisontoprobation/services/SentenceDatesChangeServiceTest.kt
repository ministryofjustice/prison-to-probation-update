package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions

import org.junit.jupiter.api.Test

internal class SentenceDatesChangeServiceTest {
  private val telemetryClient: TelemetryClient = mock()

  private val service = SentenceDatesChangeService(telemetryClient)

  @Test
  fun `will log we are processing a booking reassign`() {
    service.checkSentenceDateChangeAndUpdateProbation(SentenceDatesChangeMessage(12345L))

    verify(telemetryClient).trackEvent(eq("P2PSentenceDatesChanged"), check {
      Assertions.assertThat(it["bookingId"]).isEqualTo("12345")
    }, isNull())
  }
}