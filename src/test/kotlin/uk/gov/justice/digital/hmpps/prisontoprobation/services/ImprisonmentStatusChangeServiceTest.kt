@file:Suppress("ClassName")
package uk.gov.justice.digital.hmpps.prisontoprobation.services
import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ImprisonmentStatusChangeServiceTest {
  private val telemetryClient: TelemetryClient = mock()
  private val offenderService: OffenderService = mock()

  private val service = ImprisonmentStatusChangeService(telemetryClient, offenderService)

  @Nested
  inner class CheckImprisonmentStatusChangeAndUpdateProbation {

    @Nested
    inner class `when a successful change is processed` {
      @BeforeEach
      fun setup() {
        whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.of(2020, 2, 29)))
        whenever(offenderService.getBooking(any())).thenReturn(Booking(bookingNo = "38339A", activeFlag = true, offenderNo = "A5089DY"))
        service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))
      }

      @Test
      fun `will request the sentence start date`() {
        verify(offenderService).getSentenceDetail(12345L)
      }

      @Test
      fun `will request the booking number`() {
        verify(offenderService).getBooking(12345L)
      }

      @Test
      @Disabled
      fun `will send update to probation`() {
        TODO("implement community-api endpoint first")
      }

      @Test
      fun `will log we have processed an imprisonment status change`() {
        verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusUpdated"), check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["bookingNumber"]).isEqualTo("38339A")
          assertThat(it["sentenceStartDate"]).isEqualTo("2020-02-29")
          assertThat(it["imprisonmentStatusSeq"]).isEqualTo("88")
        }, isNull())
      }
    }


    @Test
    fun `will log that offender has no sentence date and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = null))

      service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusNoSentenceStartDate"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("88")
      }, isNull())

      verify(offenderService, never()).getBooking(any())
    }

    @Test
    fun `will log that offender has no active booking and abandon update`() {
      whenever(offenderService.getSentenceDetail(any())).thenReturn(SentenceDetail(sentenceStartDate = LocalDate.now()))
      whenever(offenderService.getBooking(any())).thenReturn(Booking(bookingNo = "A1234", activeFlag = false, offenderNo = "A5089DY"))

      service.checkImprisonmentStatusChangeAndUpdateProbation(ImprisonmentStatusChangesMessage(12345L, 88L))

      verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusIgnored"), check {
        assertThat(it["bookingId"]).isEqualTo("12345")
        assertThat(it["imprisonmentStatusSeq"]).isEqualTo("88")
      }, isNull())
    }
  }

  @Nested
  inner class CheckSentenceImposedAndUpdateProbation {
    @Test
    fun `will log we are processing a sentence imposed`() {
      service.checkSentenceImposedAndUpdateProbation(SentenceImposedMessage("A5081DY"))

      verify(telemetryClient).trackEvent(eq("P2PSentenceImposed"), check {
        assertThat(it["offenderNo"]).isEqualTo("A5081DY")
      }, isNull())
    }
  }
}
