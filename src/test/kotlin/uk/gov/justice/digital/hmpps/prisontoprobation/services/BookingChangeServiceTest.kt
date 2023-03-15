package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BookingChangeServiceTest {
  private val telemetryClient: TelemetryClient = mock()
  private val offenderService: OffenderService = mock()
  private val communityService: CommunityService = mock()

  private val service = BookingChangeService(telemetryClient, offenderService, communityService, listOf("MDI", "WII"))

  @Nested
  inner class Validate {
    @BeforeEach
    internal fun setUp() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "MDI"))
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf())
    }

    @Test
    internal fun `will retrieve booking to get offender number`() {
      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(offenderService).getBooking(12345L)
    }

    @Test
    internal fun `will retrieve merged offender ids`() {
      service.validateBookingNumberChange(BookingNumberChangedMessage(12345L))

      verify(offenderService).getMergedIdentifiers(12345L)
    }

    @Test
    internal fun `will return Done if prison not in pilot list`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "XXX"))

      val result = service.validateBookingNumberChange(BookingNumberChangedMessage(12345L))

      assertThat(result).isInstanceOf(Done::class.java)
      verify(offenderService, never()).getMergedIdentifiers(any())
    }

    @Test
    internal fun `will return Done if no merged identifiers found`() {
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf())
      val result = service.validateBookingNumberChange(BookingNumberChangedMessage(12345L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will return TryLater if merged identifiers found`() {
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf(BookingIdentifier(type = "MERGED", value = "A12345YY")))
      val result = service.validateBookingNumberChange(BookingNumberChangedMessage(12345L))

      assertThat(result).isInstanceOf(TryLater::class.java)
    }
  }

  @Nested
  inner class Process {
    @BeforeEach
    internal fun setUp() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "MDI"))
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf())
    }

    @Test
    internal fun `will retrieve booking to get offender number`() {
      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(offenderService).getBooking(12345L)
    }

    @Test
    internal fun `will retrieve merged offender ids`() {
      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(offenderService).getMergedIdentifiers(12345L)
    }

    @Test
    internal fun `will send new and old noms number to probation when present`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(offenderNo = "A11111Y"))
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf(BookingIdentifier(type = "MERGED", value = "A88888Y")))

      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(communityService).replaceProbationOffenderNo("A88888Y", "A11111Y")
    }

    @Test
    internal fun `will send new and old noms number to probation when present for each merged offender`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(offenderNo = "A11111Y"))
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf(BookingIdentifier(type = "MERGED", value = "A88888Y"), BookingIdentifier(type = "MERGED", value = "A99999Y")))

      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(communityService).replaceProbationOffenderNo("A88888Y", "A11111Y")
      verify(communityService).replaceProbationOffenderNo("A99999Y", "A11111Y")
    }

    @Test
    internal fun `will do nothing if no merged offenders found`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(offenderNo = "A11111Y"))
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf())

      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(communityService, never()).replaceProbationOffenderNo(any(), any())
    }

    @Test
    internal fun `will do nothing if booking is no longer in pilot list`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "XXX"))

      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(communityService, never()).replaceProbationOffenderNo(any(), any())
      verify(offenderService, never()).getMergedIdentifiers(any())
    }

    @Test
    fun `will log we are processing a booking number changed`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(offenderNo = "A11111Y"))
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf(BookingIdentifier(type = "MERGED", value = "A88888Y")))
      whenever(communityService.replaceProbationOffenderNo(any(), any())).thenReturn(listOf(IDs(crn = "X123456")))

      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(telemetryClient).trackEvent(
        eq("P2PBookingNumberChanged"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["offenderNo"]).isEqualTo("A11111Y")
          assertThat(it["oldOffenderNo"]).isEqualTo("A88888Y")
          assertThat(it["crn"]).isEqualTo("X123456")
        },
        isNull(),
      )
    }

    @Test
    fun `will log when no offender found in probation that can be updated`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(offenderNo = "A11111Y"))
      whenever(offenderService.getMergedIdentifiers(any())).thenReturn(listOf(BookingIdentifier(type = "MERGED", value = "A88888Y")))
      whenever(communityService.replaceProbationOffenderNo(any(), any())).thenReturn(null)

      service.processBookingNumberChangedAndUpdateProbation(BookingNumberChangedMessage(12345L))

      verify(telemetryClient).trackEvent(
        eq("P2PBookingNumberChangedOffenderNotFound"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["offenderNo"]).isEqualTo("A11111Y")
          assertThat(it["oldOffenderNo"]).isEqualTo("A88888Y")
        },
        isNull(),
      )
    }
  }
}
