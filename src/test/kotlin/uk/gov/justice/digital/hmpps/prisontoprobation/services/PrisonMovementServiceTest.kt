package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import java.time.LocalDateTime

class PrisonMovementServiceTest {
  private val offenderService: OffenderService = mock()
  private val communityService: CommunityService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val unretryableEventMetricsService: UnretryableEventMetricsService = mock()

  private lateinit var service: PrisonMovementService

  @BeforeEach
  fun before() {
    service = PrisonMovementService(offenderService, communityService, telemetryClient, unretryableEventMetricsService, listOf("MDI", "WII"))
  }

  @Nested
  internal inner class Validate {
    @BeforeEach
    fun before() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement())
    }

    @Test
    internal fun `will mark as done if movement is not longer present`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(null)

      val result = service.validateMovement(ExternalPrisonerMovementMessage(12345L, 1L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as done if movement is not an admission into prison`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

      val result = service.validateMovement(ExternalPrisonerMovementMessage(12345L, 1L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as done if movement is not prison in roll-out list`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement(toAgency = "XXX"))

      val result = service.validateMovement(ExternalPrisonerMovementMessage(12345L, 1L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as done if booking is not active`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement(toAgency = "MDI"))
      whenever(offenderService.getBooking(anyLong())).thenReturn(createInactiveBooking())

      val result = service.validateMovement(ExternalPrisonerMovementMessage(12345L, 1L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as needing processing if a valid movement`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement(toAgency = "MDI"))
      whenever(offenderService.getBooking(anyLong())).thenReturn(createCurrentBooking())

      val result = service.validateMovement(ExternalPrisonerMovementMessage(12345L, 1L))

      assertThat(result).isInstanceOf(TryLater::class.java)
    }
  }

  @Nested
  internal inner class Process {
    @BeforeEach
    fun before() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement())
      whenever(offenderService.getOffender(anyString())).thenReturn(createPrisoner())
      whenever(offenderService.getBooking(anyLong())).thenReturn(createCurrentBooking())
      whenever(communityService.updateProbationCustody(anyString(), anyString(), any())).thenReturn(createUpdatedCustody())
    }

    @Test
    fun `will retrieve the associated movement`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(offenderService).getMovement(12345L, 1L)
    }

    @Test
    fun `will always log movement`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(telemetryClient).trackEvent(
        eq("P2PExternalMovement"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["movementSeq"]).isEqualTo("1")
        },
        isNull()
      )
    }

    @Test
    fun `will retrieve the associated booking when a prison admission`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(offenderService).getBooking(12345L)
    }

    @Test
    fun `will retrieve the prisoner when a prison admission`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement("AB123D"))
      whenever(offenderService.getOffender(anyString())).thenReturn(createPrisoner())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(offenderService).getOffender("AB123D")
    }

    @Test
    fun `will log we are ignoring event when not an admission`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(telemetryClient).trackEvent(
        eq("P2PTransferIgnored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["movementType"]).isEqualTo("TRN")
          assertThat(it["fromAgency"]).isEqualTo("LEI")
          assertThat(it["toAgency"]).isEqualTo("MDI")
          assertThat(it["reason"]).isEqualTo("Not a transfer")
        },
        isNull()
      )
    }

    @Test
    fun `will log we are ignoring event when no from or to agency`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTemporaryAbsenceMovement())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(telemetryClient).trackEvent(
        eq("P2PTransferIgnored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["movementType"]).isEqualTo("TAP")
          assertThat(it["fromAgency"]).isEqualTo("not present")
          assertThat(it["toAgency"]).isEqualTo("not present")
          assertThat(it["reason"]).isEqualTo("Not a transfer")
        },
        isNull()
      )
    }

    @Test
    fun `will log we are ignoring event when booking is not active`() {
      whenever(offenderService.getBooking(anyLong())).thenReturn(createInactiveBooking())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(telemetryClient).trackEvent(
        eq("P2PTransferIgnored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["movementType"]).isEqualTo("ADM")
          assertThat(it["fromAgency"]).isEqualTo("ABRYCT")
          assertThat(it["toAgency"]).isEqualTo("MDI")
          assertThat(it["reason"]).isEqualTo("Not an active booking")
        },
        isNull()
      )
    }

    @Test
    fun `will log we are ignoring event when booking in interested prison list`() {
      service = PrisonMovementService(offenderService, communityService, telemetryClient, unretryableEventMetricsService, listOf("HUI", "WII"))

      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement("AB123D", "MDI"))
      whenever(offenderService.getBooking(anyLong())).thenReturn(createCurrentBooking())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(telemetryClient).trackEvent(
        eq("P2PTransferIgnored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["movementType"]).isEqualTo("ADM")
          assertThat(it["fromAgency"]).isEqualTo("ABRYCT")
          assertThat(it["toAgency"]).isEqualTo("MDI")
          assertThat(it["reason"]).isEqualTo("Not an interested prison")
        },
        isNull()
      )
    }

    @Test
    fun `will allow any prison when interested prison list is empty`() {
      service = PrisonMovementService(offenderService, communityService, telemetryClient, unretryableEventMetricsService, listOf())
      whenever(communityService.updateProbationCustody(anyString(), anyString(), any())).thenReturn(createUpdatedCustody("Moorland"))

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(telemetryClient).trackEvent(eq("P2PTransferProbationUpdated"), any(), isNull())
    }

    @Test
    fun `will not retrieve prison details when not an admission`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createTransferMovement())

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(offenderService, never()).getOffender(anyString())
    }

    @Test
    fun `will request probation updates custody`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement("AB123D", "MDI"))
      whenever(offenderService.getOffender(anyString())).thenReturn(createPrisoner())
      whenever(offenderService.getBooking(anyLong())).thenReturn(createCurrentBooking("38353A"))

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(communityService).updateProbationCustody(eq("AB123D"), eq("38353A"), check { assertThat(it.nomsPrisonInstitutionCode).isEqualTo("MDI") })
    }

    @Test
    fun `will log probation updated custody successfully when all ok`() {
      whenever(offenderService.getBooking(anyLong())).thenReturn(createCurrentBooking("38353A"))
      whenever(communityService.updateProbationCustody(anyString(), anyString(), any())).thenReturn(createUpdatedCustody("Moorland"))

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(telemetryClient).trackEvent(
        eq("P2PTransferProbationUpdated"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["latestLocation"]).isEqualTo("Moorland (HMP & YOI)")
          assertThat(it["convictedStatus"]).isEqualTo("Convicted")
          assertThat(it["fromAgency"]).isEqualTo("ABRYCT")
          assertThat(it["toAgency"]).isEqualTo("MDI")
          assertThat(it["toAgencyDescription"]).isEqualTo("Moorland")
        },
        isNull()
      )
    }

    @Test
    fun `will log probation did not update custody successfully when record not found`() {
      whenever(offenderService.getBooking(anyLong())).thenReturn(createCurrentBooking("38353A"))
      whenever(communityService.updateProbationCustody(anyString(), anyString(), any())).thenReturn(null)

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(telemetryClient).trackEvent(
        eq("P2PTransferProbationRecordNotFound"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["latestLocation"]).isEqualTo("Moorland (HMP & YOI)")
          assertThat(it["convictedStatus"]).isEqualTo("Convicted")
          assertThat(it["fromAgency"]).isEqualTo("ABRYCT")
          assertThat(it["toAgency"]).isEqualTo("MDI")
        },
        isNull()
      )
    }
  }

  @Nested
  inner class Metrics {
    @BeforeEach
    fun before() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createPrisonAdmissionMovement())
      whenever(offenderService.getOffender(anyString())).thenReturn(createPrisoner())
      whenever(offenderService.getBooking(anyLong())).thenReturn(createCurrentBooking())
      whenever(communityService.updateProbationCustody(anyString(), anyString(), any())).thenReturn(createUpdatedCustody())
    }

    @Test
    fun `will not count anything if movement ignored`() {
      whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(null)

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verifyNoMoreInteractions(unretryableEventMetricsService)
    }

    @Test
    fun `will count movement request succeeded`() {
      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(unretryableEventMetricsService).movementReceived()
      verify(unretryableEventMetricsService).movementSucceeded()
    }

    @Test
    fun `will count movement request failed`() {
      whenever(communityService.updateProbationCustody(anyString(), anyString(), any())).thenReturn(null)

      service.processMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

      verify(communityService).updateProbationCustody(anyString(), anyString(), any())
      verify(unretryableEventMetricsService).movementReceived()
      verify(unretryableEventMetricsService).movementFailed()
    }
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
    receptionDate = ""
  )

  private fun createPrisonAdmissionMovement(offenderNo: String = "AB123D", toAgency: String = "MDI") = Movement(
    offenderNo = offenderNo,
    createDateTime = LocalDateTime.now(),
    fromAgency = "ABRYCT",
    toAgency = toAgency,
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

  private fun createTemporaryAbsenceMovement() = Movement(
    offenderNo = "AB123D",
    createDateTime = LocalDateTime.now(),
    fromAgency = null,
    toAgency = null,
    movementType = "TAP",
    directionCode = "OUT"
  )

  private fun createCurrentBooking(bookingNo: String = "38353A") = createBooking(bookingNo = bookingNo)

  private fun createInactiveBooking() = createBooking(activeFlag = false)

  private fun createUpdatedCustody(description: String = "Moorland") = Custody(
    institution = Institution(description),
    bookingNumber = "38353A"
  )
}
