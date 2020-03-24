package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isNull
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OffenderProbationMatchServiceTest {
  private val offenderSearchService: OffenderSearchService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val service = OffenderProbationMatchService(telemetryClient, offenderSearchService)

  @Test
  fun `will call matching service`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(listOf()))

    service.ensureOffenderNumberExistsInProbation(bookingOf(
        offenderNo = "AB123D",
        firstName = "John",
        lastName = "Smith",
        dateOfBirth = LocalDate.of(1965, 7, 19)
    ))

    verify(offenderSearchService).matchProbationOffender(check {
      assertThat(it.activeSentence).isTrue()
      assertThat(it.dateOfBirth).isEqualTo(LocalDate.of(1965, 7, 19))
      assertThat(it.firstName).isEqualTo("John")
      assertThat(it.surname).isEqualTo("Smith")
      assertThat(it.nomsNumber).isEqualTo("AB123D")
    })
  }

  @Test
  fun `will return the offender number`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(listOf()))

    val offenderNo = service.ensureOffenderNumberExistsInProbation(bookingOf(
        offenderNo = "A5089DY")).onIgnore { return }
    assertThat(offenderNo).isEqualTo("A5089DY")
  }

  @Test
  fun `will send telemetry event with a match summary`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))), OffenderMatch(OffenderDetail(otherIds = IDs(crn = "A12345"))))))

    service.ensureOffenderNumberExistsInProbation(bookingOf(
        offenderNo = "A5089DY",
        bookingNo = "38339A")).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["matches"]).isEqualTo("2")
      assertThat(it["crns"]).isEqualTo("X12345, A12345")
      assertThat(it["offenderNo"]).isEqualTo("A5089DY")
      assertThat(it["bookingNumber"]).isEqualTo("38339A")
    }, isNull())
  }

  private fun bookingOf(
      bookingNo: String = "38339A",
      offenderNo: String = "A12344",
      firstName: String = "Joe",
      lastName: String = "Plumb",
      dateOfBirth: LocalDate = LocalDate.now().minusYears(20)
  ) = Booking(
      bookingNo = bookingNo,
      activeFlag = true,
      offenderNo = offenderNo,
      agencyId = "MDI",
      firstName = firstName,
      lastName = lastName,
      dateOfBirth = dateOfBirth)
}
