package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success
import java.time.LocalDate

internal class SentenceDatesChangeServiceTest {
  private val telemetryClient: TelemetryClient = mock()
  private val offenderService: OffenderService = mock()
  private val communityService: CommunityService = mock()
  private val unretryableEventMetricsService: UnretryableEventMetricsService = mock()

  private val service = SentenceDatesChangeService(telemetryClient, offenderService, communityService, unretryableEventMetricsService, listOf("MDI", "WII"))

  @Nested
  internal inner class Validate {
    @BeforeEach
    fun setup() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking())
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail())
      whenever(communityService.replaceProbationCustodyKeyDates(anyString(), anyString(), any())).thenReturn(Success(Custody(Institution("HMP Brixton"), "38339A")))
    }

    @Test
    internal fun `will mark as done if booking is not active`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(activeFlag = false))

      val result = service.validateSentenceDateChange(SentenceKeyDateChangeMessage(12345L))

      assertThat(result).isInstanceOf(Done::class.java)
    }

    @Test
    internal fun `will mark as done if prisoner is not prison in roll-out list`() {
      whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "XX"))

      val result = service.validateSentenceDateChange(SentenceKeyDateChangeMessage(12345L))

      assertThat(result).isInstanceOf(Done::class.java)
    }
  }

  @Nested
  internal inner class Process {
    @Nested
    inner class WhenSentenceDatesChange {
      @BeforeEach
      fun setup() {
        whenever(offenderService.getBooking(any())).thenReturn(createBooking())
        whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail())
        whenever(communityService.replaceProbationCustodyKeyDates(anyString(), anyString(), any())).thenReturn(Success(Custody(Institution("HMP Brixton"), "38339A")))
      }

      @Test
      fun `will check the state of the booking`() {
        service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

        verify(offenderService).getBooking(12345L)
      }

      @Test
      fun `will retrieve the sentence dates for the booking`() {
        service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

        verify(offenderService).getSentenceDetail(12345L)
      }

      @Test
      fun `will send dates to probation`() {
        whenever(offenderService.getBooking(any())).thenReturn(
          createBooking(
            bookingNo = "38339A",
            offenderNo = "A5089DY"
          )
        )
        whenever(offenderService.getSentenceDetail(any())).thenReturn(
          SentenceDetail(
            conditionalReleaseDate = LocalDate.of(1970, 1, 2),
            conditionalReleaseOverrideDate = LocalDate.of(1970, 1, 3),
            confirmedReleaseDate = LocalDate.of(1970, 1, 4),
            licenceExpiryDate = LocalDate.of(1970, 1, 5),
            paroleEligibilityDate = LocalDate.of(1970, 1, 6),
            releaseDate = LocalDate.of(1970, 1, 7),
            sentenceExpiryDate = LocalDate.of(1970, 1, 8),
            topupSupervisionExpiryDate = LocalDate.of(1970, 1, 9),
            homeDetentionCurfewEligibilityDate = LocalDate.of(1970, 1, 10)
          )
        )
        service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

        verify(communityService).replaceProbationCustodyKeyDates(
          eq("A5089DY"),
          eq("38339A"),
          check {
            assertThat(it.conditionalReleaseDate).isEqualTo(LocalDate.of(1970, 1, 3))
            assertThat(it.licenceExpiryDate).isEqualTo(LocalDate.of(1970, 1, 5))
            assertThat(it.hdcEligibilityDate).isEqualTo(LocalDate.of(1970, 1, 10))
            assertThat(it.paroleEligibilityDate).isEqualTo(LocalDate.of(1970, 1, 6))
            assertThat(it.sentenceExpiryDate).isEqualTo(LocalDate.of(1970, 1, 8))
            assertThat(it.expectedReleaseDate).isEqualTo(LocalDate.of(1970, 1, 4))
            assertThat(it.postSentenceSupervisionEndDate).isEqualTo(LocalDate.of(1970, 1, 9))
          }
        )
      }

      @Test
      fun `will send the confirmed release date to probation as expectedReleaseDate`() {
        whenever(offenderService.getBooking(any())).thenReturn(
          createBooking(
            bookingNo = "38339A",
            offenderNo = "A5089DY"
          )
        )
        whenever(offenderService.getSentenceDetail(any())).thenReturn(
          SentenceDetail(
            confirmedReleaseDate = LocalDate.parse("2020-06-12"),
            releaseDate = LocalDate.parse("2020-07-14")
          )
        )
        service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

        verify(communityService).replaceProbationCustodyKeyDates(
          eq("A5089DY"),
          eq("38339A"),
          check {
            assertThat(it.expectedReleaseDate).isEqualTo(LocalDate.parse("2020-06-12"))
          }
        )
      }

      @Test
      fun `will only send the confirmed release date to probation if present`() {
        whenever(offenderService.getBooking(any())).thenReturn(
          createBooking(
            bookingNo = "38339A",
            offenderNo = "A5089DY"
          )
        )
        whenever(offenderService.getSentenceDetail(any())).thenReturn(
          SentenceDetail(
            confirmedReleaseDate = null,
            releaseDate = LocalDate.parse("2020-07-14")
          )
        )
        service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

        verify(communityService).replaceProbationCustodyKeyDates(
          eq("A5089DY"),
          eq("38339A"),
          check {
            assertThat(it.expectedReleaseDate).isNull()
          }
        )
      }

      @Nested
      inner class WhenNoDates() {
        @Test
        fun `will send the absence of dates to probation`() {
          whenever(offenderService.getBooking(any())).thenReturn(
            createBooking(
              bookingNo = "38339A",
              offenderNo = "A5089DY"
            )
          )
          whenever(offenderService.getSentenceDetail(any())).thenReturn(
            SentenceDetail()
          )
          service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

          verify(communityService).replaceProbationCustodyKeyDates(
            eq("A5089DY"),
            eq("38339A"),
            check {
              assertThat(it.conditionalReleaseDate).isNull()
              assertThat(it.licenceExpiryDate).isNull()
              assertThat(it.hdcEligibilityDate).isNull()
              assertThat(it.paroleEligibilityDate).isNull()
              assertThat(it.sentenceExpiryDate).isNull()
              assertThat(it.expectedReleaseDate).isNull()
              assertThat(it.postSentenceSupervisionEndDate).isNull()
            }
          )
        }
      }

      @Nested
      inner class WhenConditionalOverrideDate() {
        @Test
        fun `will send the override conditional release date to probation`() {
          whenever(offenderService.getBooking(any())).thenReturn(
            createBooking(
              bookingNo = "38339A",
              offenderNo = "A5089DY"
            )
          )
          whenever(offenderService.getSentenceDetail(any())).thenReturn(
            SentenceDetail(
              conditionalReleaseDate = LocalDate.of(1970, 1, 2),
              conditionalReleaseOverrideDate = LocalDate.of(1970, 1, 3)
            )
          )
          service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

          verify(communityService).replaceProbationCustodyKeyDates(
            eq("A5089DY"),
            eq("38339A"),
            check {
              assertThat(it.conditionalReleaseDate).isEqualTo(LocalDate.of(1970, 1, 3))
            }
          )
        }
      }

      @Nested
      inner class WhenNoConditionalOverrideDate {
        @Test
        fun `will send the original conditional release of date to probation`() {
          whenever(offenderService.getBooking(any())).thenReturn(
            createBooking(
              bookingNo = "38339A",
              offenderNo = "A5089DY"
            )
          )
          whenever(offenderService.getSentenceDetail(any())).thenReturn(
            SentenceDetail(
              conditionalReleaseDate = LocalDate.of(1970, 1, 2)
            )
          )
          service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

          verify(communityService).replaceProbationCustodyKeyDates(
            eq("A5089DY"),
            eq("38339A"),
            check {
              assertThat(it.conditionalReleaseDate).isEqualTo(LocalDate.of(1970, 1, 2))
            }
          )
        }
      }

      @Test
      fun `will log we have processed a sentence date change`() {
        whenever(offenderService.getBooking(any())).thenReturn(
          createBooking(
            bookingNo = "38339A",
            offenderNo = "A5089DY",
            firstName = "Bobby",
            lastName = "Bridle",
            dateOfBirth = LocalDate.of(1965, 7, 19)
          )
        )
        whenever(offenderService.getSentenceDetail(any())).thenReturn(
          SentenceDetail(
            sentenceStartDate = LocalDate.of(1970, 1, 1),
            conditionalReleaseDate = LocalDate.of(1970, 1, 2),
            conditionalReleaseOverrideDate = LocalDate.of(1970, 1, 3),
            confirmedReleaseDate = LocalDate.of(1970, 1, 4),
            licenceExpiryDate = LocalDate.of(1970, 1, 5),
            paroleEligibilityDate = LocalDate.of(1970, 1, 6),
            releaseDate = LocalDate.of(1970, 1, 7),
            sentenceExpiryDate = LocalDate.of(1970, 1, 8),
            topupSupervisionExpiryDate = LocalDate.of(1970, 1, 9)
          )
        )

        service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

        verify(telemetryClient).trackEvent(
          eq("P2PSentenceDatesChanged"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["bookingNumber"]).isEqualTo("38339A")
            assertThat(it["offenderNo"]).isEqualTo("A5089DY")
            assertThat(it["sentenceStartDate"]).isEqualTo("1970-01-01")
            assertThat(it["conditionalReleaseDate"]).isEqualTo("1970-01-02")
            assertThat(it["conditionalReleaseOverrideDate"]).isEqualTo("1970-01-03")
            assertThat(it["confirmedReleaseDate"]).isEqualTo("1970-01-04")
            assertThat(it["licenceExpiryDate"]).isEqualTo("1970-01-05")
            assertThat(it["paroleEligibilityDate"]).isEqualTo("1970-01-06")
            assertThat(it["releaseDate"]).isEqualTo("1970-01-07")
            assertThat(it["sentenceExpiryDate"]).isEqualTo("1970-01-08")
            assertThat(it["topupSupervisionExpiryDate"]).isEqualTo("1970-01-09")
          },
          isNull()
        )
      }

      @Nested
      inner class WhenProbationRecordNotFound {
        @Test
        fun `will log we have tried to process a sentence date change but record not found `() {
          whenever(communityService.replaceProbationCustodyKeyDates(anyString(), anyString(), any())).thenReturn(Ignore("not found error message"))

          whenever(offenderService.getBooking(any())).thenReturn(
            createBooking(
              bookingNo = "38339A",
              offenderNo = "A5089DY"
            )
          )
          service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

          verify(telemetryClient).trackEvent(
            eq("P2PSentenceDatesRecordNotFound"),
            check {
              assertThat(it["bookingId"]).isEqualTo("12345")
              assertThat(it["bookingNumber"]).isEqualTo("38339A")
              assertThat(it["offenderNo"]).isEqualTo("A5089DY")
            },
            isNull()
          )
        }
      }
    }

    @Nested
    inner class WhenNotAnActiveBooking {
      @BeforeEach
      fun setup() {
        whenever(offenderService.getBooking(any())).thenReturn(createBooking(activeFlag = false))
      }

      @Test
      fun `will log that offender has no active booking and abandon update`() {
        service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

        verify(telemetryClient).trackEvent(
          eq("P2PSentenceDatesChangeIgnored"),
          check {
            assertThat(it["reason"]).isEqualTo("Not an active booking")
            assertThat(it["bookingId"]).isEqualTo("12345")
          },
          isNull()
        )

        verify(communityService, never()).replaceProbationCustodyKeyDates(any(), any(), any())
      }
    }

    @Nested
    inner class WhenBookingNotInAnInterestedPrison {
      @BeforeEach
      fun setup() {
        whenever(offenderService.getBooking(any())).thenReturn(createBooking(agencyId = "XX"))
      }

      @Test
      fun `will log that offender is not in an interested prison and abandon update`() {
        service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

        verify(telemetryClient).trackEvent(
          eq("P2PSentenceDatesChangeIgnored"),
          check {
            assertThat(it["reason"]).isEqualTo("Not at an interested prison")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["agencyId"]).isEqualTo("XX")
          },
          isNull()
        )
        verify(communityService, never()).replaceProbationCustodyKeyDates(any(), any(), any())
      }
    }
  }

  @Nested
  inner class Metrics {
    @BeforeEach
    fun before() {
      whenever(offenderService.getBooking(anyLong())).thenReturn(createBooking())
      whenever(offenderService.getSentenceDetail(anyLong())).thenReturn(SentenceDetail())
    }

    @Test
    fun `will not count anything if date change ignored`() {
      whenever(offenderService.getBooking(anyLong())).thenReturn(createBooking(activeFlag = false))

      service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

      verifyNoMoreInteractions(unretryableEventMetricsService)
    }

    @Test
    fun `will count date change succeeded`() {
      whenever(communityService.replaceProbationCustodyKeyDates(anyString(), anyString(), any()))
        .thenReturn(Success(Custody(Institution("LEI"), "AA1234A")))

      service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

      verify(unretryableEventMetricsService).dateChangeReceived()
      verify(unretryableEventMetricsService).dateChangeSucceeded()
      verifyNoMoreInteractions(unretryableEventMetricsService)
    }

    @Test
    fun `will count date change failed - offender not found`() {
      whenever(communityService.replaceProbationCustodyKeyDates(anyString(), anyString(), any()))
        .thenReturn(Ignore("Offender with NOMS number AA1234A not found"))

      service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

      verify(unretryableEventMetricsService).dateChangeReceived()
      verify(unretryableEventMetricsService).dateChangeFailedNoOffender()
      verifyNoMoreInteractions(unretryableEventMetricsService)
    }

    @Test
    fun `will count date change failed - conviction not found`() {
      whenever(communityService.replaceProbationCustodyKeyDates(anyString(), anyString(), any()))
        .thenReturn(Ignore("Conviction with bookingNumber 12345 not found for offender with NOMS number AA1234A"))

      service.processSentenceDateChangeAndUpdateProbation(SentenceKeyDateChangeMessage(12345L))

      verify(unretryableEventMetricsService).dateChangeReceived()
      verify(unretryableEventMetricsService).dateChangeFailedNoConviction()
      verifyNoMoreInteractions(unretryableEventMetricsService)
    }
  }
}
