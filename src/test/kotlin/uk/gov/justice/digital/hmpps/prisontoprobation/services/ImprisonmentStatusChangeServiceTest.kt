@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    internal fun `will mark as done if status change is not significant`() {
      val result = service.validateImprisonmentStatusChange(ImprisonmentStatusChangesMessage(12345L, 88L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as done if sentence has no start date`() {
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
      whenever(offenderProbationMatchService.ensureOffenderNumberExistsInProbation(any(), any())).thenAnswer { Success((it.arguments[0] as Booking).offenderNo to "X12345") }
    }

    @Nested
    inner class StatusChange {
      @BeforeEach
      fun setup() {
        whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.of(2019, 4, 12)))
        whenever(offenderService.getCurrentSentences(any())).thenReturn(
          listOf(
            SentenceSummary(startDate = LocalDate.of(2020, 2, 29), sentenceSequence = 33, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
            SentenceSummary(startDate = LocalDate.of(2019, 4, 12), sentenceSequence = 34, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
          ),
        )
        whenever(offenderService.getBooking(any())).thenReturn(createBooking())
        whenever(communityService.updateProbationCustodyBookingNumber(anyString(), any())).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))
      }

      @Test
      fun `will request the booking number`() {
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(offenderService).getBooking(12345L)
      }

      @Test
      fun `will check offender exists in probation using latest primary sentence date`() {
        val booking = createBooking()
        whenever(offenderService.getBooking(any())).thenReturn(booking)
        whenever(offenderService.getCurrentSentences(any())).thenReturn(
          listOf(
            SentenceSummary(startDate = LocalDate.parse("2020-02-28"), consecutiveTo = 33, sentenceSequence = 32, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
            SentenceSummary(startDate = LocalDate.parse("2020-01-30"), sentenceSequence = 33, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
            SentenceSummary(startDate = LocalDate.parse("2019-06-23"), sentenceSequence = 34, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
            SentenceSummary(startDate = null, sentenceSequence = 34, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
          ),
        )

        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(offenderProbationMatchService).ensureOffenderNumberExistsInProbation(booking, LocalDate.parse("2020-01-30"))
      }

      @Test
      fun `will send bookingNumber and latest primary sentence date update to probation`() {
        whenever(offenderService.getCurrentSentences(any())).thenReturn(
          listOf(
            SentenceSummary(startDate = LocalDate.parse("2020-02-28"), consecutiveTo = 33, sentenceSequence = 32, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
            SentenceSummary(startDate = LocalDate.parse("2020-01-30"), sentenceSequence = 33, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
            SentenceSummary(startDate = LocalDate.parse("2019-06-23"), sentenceSequence = 34, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
            SentenceSummary(startDate = null, sentenceSequence = 34, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
          ),
        )

        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(communityService).updateProbationCustodyBookingNumber("A5089DY", UpdateCustodyBookingNumber(LocalDate.parse("2020-01-30"), "38339A"))
      }

      @Test
      fun `will log we have processed an imprisonment status change`() {
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        verify(telemetryClient).trackEvent(
          eq("P2PImprisonmentStatusUpdated"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["bookingNumber"]).isEqualTo("38339A")
            assertThat(it["sentenceStartDate"]).isEqualTo("2020-02-29")
            assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
            assertThat(it["offenderNo"]).isEqualTo("A5089DY")
          },
          isNull(),
        )
      }

      @Test
      fun `will indicate we are done with this change`() {
        val result = service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))
        assertThat(result).usingRecursiveComparison().isEqualTo(
          Done(
            status = SynchroniseStatus(
              matchingCrns = "X12345",
              state = SynchroniseState.COMPLETED,
            ),
          ),
        )
      }

      @Nested
      inner class WhenMatchNotFound {
        @BeforeEach
        fun setup() {
          whenever(offenderProbationMatchService.ensureOffenderNumberExistsInProbation(any(), any())).thenReturn(
            Ignore(
              TelemetryEvent(
                name = "P2POffenderNoMatch",
                attributes = mapOf("offenderNo" to "A5089DY", "crns" to ""),
              ) to SynchroniseStatus("", state = SynchroniseState.NO_MATCH),
            ),
          )
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

          verify(telemetryClient).trackEvent(
            eq("P2PBookingNumberNotAssigned"),
            check {
              assertThat(it["bookingId"]).isEqualTo("12345")
              assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenPrisonLocationNotSet {
        private val sentenceExpiryDate: LocalDate = LocalDate.now().plusYears(1)

        @BeforeEach
        fun setup() {
          whenever(offenderService.getSentenceDetail(any())).thenReturn(
            SentenceDetail(
              sentenceStartDate = LocalDate.of(2020, 2, 29),
              sentenceExpiryDate = sentenceExpiryDate,
            ),
          )
        }
      }
    }

    @Test
    fun `will log that status change is not significant when sequence is not zero`() {
      service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

      verify(telemetryClient).trackEvent(
        eq("P2PImprisonmentStatusNotSequenceZero"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["imprisonmentStatusSeq"]).isEqualTo("88")
        },
        isNull(),
      )

      verify(offenderService, never()).getBooking(any())
    }

    @Test
    fun `will ignore when status change is not significant when sequence is not zero`() {
      val result =
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

      assertThat(result).usingRecursiveComparison()
        .isEqualTo(Done(status = SynchroniseStatus(state = SynchroniseState.NO_LONGER_VALID)))
    }

    @Test
    fun `will log that offender has no sentence date and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = null))

      service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(
        eq("P2PImprisonmentStatusNoSentenceStartDate"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
        },
        isNull(),
      )

      verify(offenderService, never()).getBooking(any())
    }

    @Test
    fun `will ignore when offender has no sentence date`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = null))

      val result =
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

      assertThat(result).usingRecursiveComparison()
        .isEqualTo(Done(status = SynchroniseStatus(state = SynchroniseState.NO_LONGER_VALID)))
    }

    @Test
    fun `will log that offender has no active booking and abandon update`() {
      whenever(offenderService.getCurrentSentences(any())).thenReturn(
        listOf(
          SentenceSummary(startDate = LocalDate.now(), sentenceSequence = 33, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
        ),
      )
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.now()))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(activeFlag = false))

      service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(
        eq("P2PImprisonmentStatusIgnored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
        },
        isNull(),
      )
    }

    @Test
    fun `will ignore when offender has no active booking`() {
      whenever(offenderService.getCurrentSentences(any())).thenReturn(
        listOf(
          SentenceSummary(
            startDate = LocalDate.now(),
            sentenceSequence = 33,
            sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence",
          ),
        ),
      )
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.now()))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(activeFlag = false))

      val result =
        service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

      assertThat(result).usingRecursiveComparison()
        .isEqualTo(Done(status = SynchroniseStatus(state = SynchroniseState.NO_LONGER_VALID)))
    }

    @Test
    fun `will log that offender has booking at prison we are not interested in and abandon update`() {
      whenever(offenderService.getCurrentSentences(any())).thenReturn(
        listOf(
          SentenceSummary(startDate = LocalDate.now(), sentenceSequence = 33, sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence"),
        ),
      )
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.now()))
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "XXX"))

      service.processImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 0L))

      verify(telemetryClient).trackEvent(
        eq("P2PImprisonmentStatusIgnored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["imprisonmentStatusSeq"]).isEqualTo("0")
          assertThat(it["reason"]).isEqualTo("Not at an interested prison")
          assertThat(it["bookingNumber"]).isEqualTo("38339A")
          assertThat(it["offenderNo"]).isEqualTo("A5089DY")
          assertThat(it["agencyId"]).isEqualTo("XXX")
        },
        isNull(),
      )
    }
  }
}
