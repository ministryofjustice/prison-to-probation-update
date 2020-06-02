package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BookingChangeServiceTest {
    private val telemetryClient: TelemetryClient = mock()

    private val service = BookingChangeService(telemetryClient)

    @Test
    fun `will log we are processing a booking number changed`() {
        service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L, 99L, "A0001", "B0002"))

        verify(telemetryClient).trackEvent(eq("P2PBookingNumberChanged"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["offenderId"]).isEqualTo("99")
            assertThat(it["bookingNumber"]).isEqualTo("A0001")
            assertThat(it["previousBookingNumber"]).isEqualTo("B0002")
        }, isNull())
    }

}

