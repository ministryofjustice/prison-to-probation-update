@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import java.time.LocalDate

class ImprisonmentStatusChangeServiceTest {
  private val telemetryClient: TelemetryClient = mock()
  private val offenderService: OffenderService = mock()
  private val communityService: CommunityService = mock()

  private val service = ImprisonmentStatusChangeService(telemetryClient, offenderService, communityService, listOf("MDI", "WII"))

  @Nested
  inner class CheckImprisonmentStatusChangeAndUpdateProbation {

    @Nested
    inner class StatusChange {
      @BeforeEach
      fun setup() {
        whenever(offenderService.getSentenceDetail(any())).thenReturn(createSentenceDetail(sentenceStartDate = LocalDate.of(2020, 2, 29)))
        whenever(offenderService.getBooking(any())).thenReturn(createBooking())
        whenever(communityService.updateProbationCustodyBookingNumber(anyString(), any())).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))
      }

      @Test
      fun `will request the sentence start date`() {
        service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(offenderService).getSentenceDetail(12345L)
      }

      @Test
      fun `will request the booking number`() {
        service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(offenderService).getBooking(12345L)
      }

      @Test
      fun `will send update to probation`() {
        service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(communityService).updateProbationCustodyBookingNumber("A5089DY", UpdateCustodyBookingNumber(LocalDate.of(2020, 2, 29), "38339A"))
      }

      @Test
      fun `will log we have processed an imprisonment status change`() {
        service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusUpdated"), check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["bookingNumber"]).isEqualTo("38339A")
          assertThat(it["sentenceStartDate"]).isEqualTo("2020-02-29")
          assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
          assertThat(it["offenderNo"]).isEqualTo("A5089DY")
          assertThat(it["firstName"]).isEqualTo("Johnny")
          assertThat(it["lastName"]).isEqualTo("Barnes")
          assertThat(it["dateOfBirth"]).isEqualTo("1965-07-19")
        }, isNull())
      }

      @Nested
      inner class WhenNotFound {
        @BeforeEach
        fun setup() {
          whenever(communityService.updateProbationCustodyBookingNumber(anyString(), any())).thenReturn(null)
        }

        @Test
        fun `will log that we have not processed an imprisonment status change`() {
          service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
          verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusRecordNotFound"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["bookingNumber"]).isEqualTo("38339A")
            assertThat(it["sentenceStartDate"]).isEqualTo("2020-02-29")
            assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
            assertThat(it["offenderNo"]).isEqualTo("A5089DY")
            assertThat(it["firstName"]).isEqualTo("Johnny")
            assertThat(it["lastName"]).isEqualTo("Barnes")
            assertThat(it["dateOfBirth"]).isEqualTo("1965-07-19")
          }, isNull())
        }
      }
    }


    @Test
    fun `will log that status change is not significant when sequence is not zero`() {
      service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusNotSequenceZero"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("88")
      }, isNull())

      verify(offenderService, never()).getBooking(any())
    }

    @Test
    fun `will log that offender has no sentence date and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(createSentenceDetail(sentenceStartDate = null))

      service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusNoSentenceStartDate"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
      }, isNull())

      verify(offenderService, never()).getBooking(any())
    }

    @Test
    fun `will log that offender has no active booking and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(createSentenceDetail(sentenceStartDate = LocalDate.now()))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(activeFlag = false))

      service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusIgnored"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
      }, isNull())
    }
    @Test
    fun `will log that offender has booking at prison we are not interested in and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(createSentenceDetail(sentenceStartDate = LocalDate.now()))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "XXX"))

      service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusIgnored"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
        assertThat(it["reason"]).isEqualTo("Not at an interested prison")
        assertThat(it["bookingNumber"]).isEqualTo("38339A")
        assertThat(it["offenderNo"]).isEqualTo("A5089DY")
        assertThat(it["firstName"]).isEqualTo("Johnny")
        assertThat(it["lastName"]).isEqualTo("Barnes")
        assertThat(it["dateOfBirth"]).isEqualTo("1965-07-19")
        assertThat(it["agencyId"]).isEqualTo("XXX")
      }, isNull())
    }
  }
}


private fun createBooking(activeFlag: Boolean = true, agencyId: String = "MDI") : Booking = Booking(
    bookingNo = "38339A",
    activeFlag = activeFlag,
    offenderNo = "A5089DY",
    agencyId = agencyId,
    firstName = "Johnny",
    lastName = "Barnes",
    dateOfBirth = LocalDate.of(1965, 7, 19)
)

private fun createSentenceDetail(sentenceStartDate: LocalDate?) : SentenceDetail = SentenceDetail(
    sentenceStartDate = sentenceStartDate,
    conditionalReleaseDate = null,
    conditionalReleaseOverrideDate = null,
    confirmedReleaseDate = null,
    licenceExpiryDate = null,
    paroleEligibilityDate = null,
    releaseDate = null,
    sentenceExpiryDate = null,
    topupSupervisionExpiryDate = null,
    homeDetentionCurfewEligibilityDate = null
)