package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class BookingChangeServiceTest {
    private val telemetryClient: TelemetryClient = mock()

    private val service = BookingChangeService(telemetryClient)

    @Test
    fun `will log we are processing a new booking`() {
        service.checkBookingCreationAndUpdateProbation(OffenderBookingInsertedMessage(12345L, 99L))

        verify(telemetryClient).trackEvent(eq("P2PBookingCreated"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["offenderId"]).isEqualTo("99")
        }, isNull())
    }

    @Test
    fun `will log we are processing a booking reassign`() {
        service.checkBookingReassignedAndUpdateProbation(OffenderBookingReassignedMessage(12345L, 99L, 101L))

        verify(telemetryClient).trackEvent(eq("P2PBookingReassigned"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["offenderId"]).isEqualTo("99")
            assertThat(it["previousOffenderId"]).isEqualTo("101")
        }, isNull())
    }

    @Test
    fun `will log we are processing a booking number changed`() {
        service.checkBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L, 99L, "A0001", "B0002"))

        verify(telemetryClient).trackEvent(eq("P2PBookingNumberChanged"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["offenderId"]).isEqualTo("99")
            assertThat(it["bookingNumber"]).isEqualTo("A0001")
            assertThat(it["previousBookingNumber"]).isEqualTo("B0002")
        }, isNull())
    }

}

