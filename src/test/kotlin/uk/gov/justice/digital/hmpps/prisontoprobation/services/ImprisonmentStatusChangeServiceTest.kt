@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success
import java.time.LocalDate

class ImprisonmentStatusChangeServiceTest {
  private val telemetryClient: TelemetryClient = mock()
  private val offenderService: OffenderService = mock()
  private val communityService: CommunityService = mock()
  private val offenderProbationMatchService: OffenderProbationMatchService = mock()

  private val service = ImprisonmentStatusChangeService(telemetryClient, offenderService, communityService, offenderProbationMatchService, listOf("MDI", "WII"))

  @Nested
  inner class Validate {
    @Test
    internal fun `will mark as done if status change is not significant`( ) {
      val result = service.validateImprisonmentStatusChange(ImprisonmentStatusChangesMessage(12345L, 88L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as done if sentence has no start date`( ) {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = null))

      val result = service.validateImprisonmentStatusChange(ImprisonmentStatusChangesMessage(12345L, 0L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as done if booking is not active`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.of(2020, 2, 29)))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(activeFlag = false))

      val result = service.validateImprisonmentStatusChange(ImprisonmentStatusChangesMessage(12345L, 0L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as done if prisoner is not prison in roll-out list`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.of(2020, 2, 29)))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "XXX"))

      val result = service.validateImprisonmentStatusChange(ImprisonmentStatusChangesMessage(12345L, 0L))

      assertThat(result).isInstanceOf(Done::class.java)
    }
  }
  @Nested
  inner class Process {
    @BeforeEach
    fun setUp() {
      whenever(offenderProbationMatchService.ensureOffenderNumberExistsInProbation(any(), any())).thenAnswer { Success((it.arguments[0] as Booking).offenderNo) }
    }

    @Nested
    inner class StatusChange {
      @BeforeEach
      fun setup() {
        whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.of(2020, 2, 29)))
        whenever(offenderService.getBooking(any())).thenReturn(createBooking())
        whenever(communityService.updateProbationCustodyBookingNumber(anyString(), any())).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))
        whenever(communityService.updateProbationCustody(anyString(), anyString(), any())).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))
        whenever(communityService.replaceProbationCustodyKeyDates(anyString(), anyString(), any())).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))
      }

      @Test
      fun `will request the sentence start date`() {
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(offenderService).getSentenceDetail(12345L)
      }

      @Test
      fun `will request the booking number`() {
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(offenderService).getBooking(12345L)
      }

      @Test
      fun `will check offender exists in probation`() {
        val booking = createBooking()
        whenever(offenderService.getBooking(any())).thenReturn(booking)
        whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.parse("2020-01-30")))

        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(offenderProbationMatchService).ensureOffenderNumberExistsInProbation(booking, LocalDate.parse("2020-01-30"))
      }

      @Test
      fun `will send bookingNumber update to probation`() {
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(communityService).updateProbationCustodyBookingNumber("A5089DY", UpdateCustodyBookingNumber(LocalDate.of(2020, 2, 29), "38339A"))
      }

      @Test
      fun `will send location to probation`() {
        whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "MDI"))

        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(communityService).updateProbationCustody("A5089DY",  "38339A", UpdateCustody(nomsPrisonInstitutionCode = "MDI"))
      }

      @Test
      fun `will send keydates to probation`() {
        whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.of(2020, 2, 29), licenceExpiryDate = LocalDate.parse("1970-01-05")))

        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(communityService).replaceProbationCustodyKeyDates(eq("A5089DY"), eq("38339A"), check {
          assertThat(it.licenceExpiryDate).isEqualTo(LocalDate.parse("1970-01-05"))
        })
      }

      @Test
      fun `will log we have processed an imprisonment status change`() {
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusUpdated"), check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["bookingNumber"]).isEqualTo("38339A")
          assertThat(it["sentenceStartDate"]).isEqualTo("2020-02-29")
          assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
          assertThat(it["offenderNo"]).isEqualTo("A5089DY")
        }, isNull())
      }

      @Test
      fun `will indicate we are done with this change`() {
        val result = service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        assertThat(result).isInstanceOf(Done::class.java)
      }

      @Nested
      inner class WhenMatchNotFound {
        @BeforeEach
        fun setup() {
          whenever(offenderProbationMatchService.ensureOffenderNumberExistsInProbation(any(), any())).thenReturn(Ignore(TelemetryEvent(name = "P2POffenderNoMatch", attributes = mapOf("offenderNo" to "A5089DY", "crns" to ""))))
        }

        @Test
        fun `will indicate we want to try again`() {
          val result = service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
          assertThat(result).isInstanceOf(TryLater::class.java)
        }
      }

      @Nested
      inner class WhenBookingNumberNotSet {
        @BeforeEach
        fun setup() {
          whenever(communityService.updateProbationCustodyBookingNumber(anyString(), any())).thenReturn(null)
        }

        @Test
        fun `will indicate we want to try again`() {
          val result = service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
          assertThat(result).isInstanceOf(TryLater::class.java)
        }
        @Test
        fun `will log that booking number could not be set`() {
          service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

          verify(telemetryClient).trackEvent(eq("P2PBookingNumberNotAssigned"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
          }, isNull())
        }
      }
      @Nested
      inner class WhenPrisonLocationNotSet {
        private val sentenceExpiryDate: LocalDate = LocalDate.now().plusYears(1)
        @BeforeEach
        fun setup() {
          whenever(communityService.updateProbationCustody(anyString(), anyString(), any())).thenReturn(null)
          whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(
              sentenceStartDate = LocalDate.of(2020, 2, 29),
              sentenceExpiryDate = sentenceExpiryDate
          ))
        }

        @Test
        fun `will indicate we want to try again until sentence expires`() {
          val result = service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

          assertThat(result).isEqualToComparingFieldByField(TryLater(bookingId = 12345, retryUntil = sentenceExpiryDate))
        }
        @Test
        fun `will log that prison location could not be set`() {
          service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

          verify(telemetryClient).trackEvent(eq("P2PLocationNotUpdated"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
          }, isNull())
        }
      }
      @Nested
      inner class WhenDatesNotSet {
        private val sentenceExpiryDate: LocalDate = LocalDate.now().plusYears(1)
        @BeforeEach
        fun setup() {
          whenever(communityService.replaceProbationCustodyKeyDates(anyString(), anyString(), any())).thenReturn(null)
          whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(
              sentenceStartDate = LocalDate.of(2020, 2, 29),
              sentenceExpiryDate = sentenceExpiryDate
          ))
        }

        @Test
        fun `will indicate we want to try again until sentence expires`() {
          val result = service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

          assertThat(result).isEqualToComparingFieldByField(TryLater(bookingId = 12345, retryUntil = sentenceExpiryDate))
        }

        @Test
        fun `will log that key dates could not be set`() {
          service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

          verify(telemetryClient).trackEvent(eq("P2PKeyDatesNotUpdated"), check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
          }, isNull())
        }
      }
    }


    @Test
    fun `will log that status change is not significant when sequence is not zero`() {
      service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusNotSequenceZero"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("88")
      }, isNull())

      verify(offenderService, never()).getBooking(any())
    }

    @Test
    fun `will log that offender has no sentence date and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = null))

      service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusNoSentenceStartDate"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
      }, isNull())

      verify(offenderService, never()).getBooking(any())
    }

    @Test
    fun `will log that offender has no active booking and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.now()))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(activeFlag = false))

      service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusIgnored"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
      }, isNull())
    }
    @Test
    fun `will log that offender has booking at prison we are not interested in and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.now()))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "XXX"))

      service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusIgnored"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
        assertThat(it["reason"]).isEqualTo("Not at an interested prison")
        assertThat(it["bookingNumber"]).isEqualTo("38339A")
        assertThat(it["offenderNo"]).isEqualTo("A5089DY")
        assertThat(it["agencyId"]).isEqualTo("XXX")
      }, isNull())
    }
  }
}
