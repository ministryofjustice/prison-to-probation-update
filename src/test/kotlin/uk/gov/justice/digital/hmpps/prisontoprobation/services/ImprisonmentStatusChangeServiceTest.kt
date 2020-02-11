package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ImprisonmentStatusChangeServiceTest {
    private val telemetryClient: TelemetryClient = mock()

    private val service = ImprisonmentStatusChangeService(telemetryClient)

    @Test
    fun `will log we are processing a imprisonment status change`() {
        service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

        verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusChanged"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["imprisonmentStatusSeq"]).isEqualTo("88")
        }, isNull())
    }


}

