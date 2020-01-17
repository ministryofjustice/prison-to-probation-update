package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import java.time.LocalDateTime

class PrisonMovementServiceTest {
    private val offenderService: OffenderService = mock()
    private val telemetryClient: TelemetryClient = mock()

    private lateinit var service: PrisonMovementService

    @Before
    fun before() {
        service = PrisonMovementService(offenderService, telemetryClient)
    }

    @Test
    fun `will retrieve the associated movement`() {
        whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

        service.checkMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

        verify(offenderService).getMovement(12345L, 1L)
    }

    @Test
    fun `will always log movement`() {
        whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

        service.checkMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

        verify(telemetryClient).trackEvent(eq("P2PExternalMovement"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("1")
        }, isNull())
    }

    @Test
    fun `will retrieve the prisoner when a prison admission`() {
        whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement("AB123D"))
        whenever(offenderService.getOffender(anyString())).thenReturn(createPrisoner())

        service.checkMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

        verify(offenderService).getOffender("AB123D")
    }

    @Test
    fun `will log we are ignoring event when not an admission`() {
        whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

        service.checkMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

        verify(telemetryClient).trackEvent(eq("P2PTransferIgnored"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementType"]).isEqualTo("TRN")
            assertThat(it["fromAgency"]).isEqualTo("LEI")
            assertThat(it["toAgency"]).isEqualTo("MDI")
        }, isNull())
    }

    @Test
    fun `will not retrieve prison details when not an admission`() {
        whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

        service.checkMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

        verify(offenderService, never()).getOffender(anyString())
    }

    @Test
    fun `will log prisoner information when a prison admission`() {
        whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement("AB123D"))
        whenever(offenderService.getOffender(anyString())).thenReturn(createPrisoner())

        service.checkMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

        verify(telemetryClient).trackEvent(eq("P2PTransferIn"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["offenderNo"]).isEqualTo("AB123D")
            assertThat(it["firstName"]).isEqualTo("Bobby")
            assertThat(it["lastName"]).isEqualTo("Jones")
            assertThat(it["latestLocation"]).isEqualTo("Moorland (HMP & YOI)")
            assertThat(it["convictedStatus"]).isEqualTo("Convicted")
            assertThat(it["fromAgency"]).isEqualTo("ABRYCT")
            assertThat(it["toAgency"]).isEqualTo("MDI")
        }, isNull())
    }

    private fun createPrisoner() = Prisoner(
            offenderNo = "AB123D",
            pncNumber = "",
            croNumber = "",
            firstName = "Bobby",
            middleNames = "David",
            lastName = "Jones",
            dateOfBirth = "1970-01-01",
            currentlyInPrison = "Y",
            latestBookingId = 1L,
            latestLocationId = "MDI",
            latestLocation = "Moorland (HMP & YOI)",
            convictedStatus = "Convicted",
            imprisonmentStatus = "",
            receptionDate = "")

    private fun createPrisonAdmissionMovement(offenderNo: String) = Movement (
            offenderNo = offenderNo,
            createDateTime = LocalDateTime.now(),
            fromAgency = "ABRYCT",
            toAgency = "MDI",
            movementType = "ADM",
            directionCode = "OUT"
    )

    private fun createTransferMovement() = Movement(
            offenderNo = "AB123D",
            createDateTime = LocalDateTime.now(),
            fromAgency = "LEI",
            toAgency = "MDI",
            movementType = "TRN",
            directionCode = "OUT"
    )
}

