package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SentenceChangeServiceTest {
    private val telemetryClient: TelemetryClient = mock()

    private val service = SentenceChangeService(telemetryClient)

    @Test
    fun `will log we are processing a sentence change`() {
        service.checkSentenceChangeAndUpdateProbation(CourtSentenceChangesMessage(12345L))

        verify(telemetryClient).trackEvent(eq("P2PBookingSentenceChanged"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
        }, isNull())
    }


}

