package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReleaseAndRecallServiceTest {
  private val communityService: CommunityService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private lateinit var service: ReleaseAndRecallService

  @BeforeEach
  fun before() {
    service = ReleaseAndRecallService(communityService, telemetryClient)
  }

  @Test
  internal fun `prisoner will be recalled`() {
    val recallDate = LocalDate.of(2021, 5, 12)
    whenever(communityService.prisonerRecalled("A5194DY", "MDI", recallDate)).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))

    service.prisonerRecalled("A5194DY", "MDI", recallDate, "RECALL")

    verify(communityService).prisonerRecalled("A5194DY", "MDI", recallDate)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerRecalled"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
        assertThat(it["prisonId"]).isEqualTo("MDI")
        assertThat(it["recallDate"]).isEqualTo(recallDate.toString())
        assertThat(it["probableCause"]).isEqualTo("RECALL")
      },
      isNull()
    )
  }

  @Test
  internal fun `prisoner not recalled`() {
    val recallDate = LocalDate.of(2021, 5, 12)
    whenever(communityService.prisonerRecalled("A5194DY", "MDI", recallDate)).thenReturn(null)

    service.prisonerRecalled("A5194DY", "MDI", recallDate, "REMAND")

    verify(communityService).prisonerRecalled("A5194DY", "MDI", recallDate)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerNotRecalled"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
        assertThat(it["prisonId"]).isEqualTo("MDI")
        assertThat(it["recallDate"]).isEqualTo(recallDate.toString())
        assertThat(it["probableCause"]).isEqualTo("REMAND")
      },
      isNull()
    )
  }

  @Test
  internal fun `prisoner will be released`() {
    val releaseDate = LocalDate.of(2021, 5, 12)
    whenever(communityService.prisonerReleased("A5194DY", "MDI", releaseDate)).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))

    service.prisonerReleased("A5194DY", "MDI", releaseDate)

    verify(communityService).prisonerReleased("A5194DY", "MDI", releaseDate)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerReleased"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
        assertThat(it["prisonId"]).isEqualTo("MDI")
        assertThat(it["releaseDate"]).isEqualTo(releaseDate.toString())
      },
      isNull()
    )
  }

  @Test
  internal fun `prisoner not released`() {
    val releaseDate = LocalDate.of(2021, 5, 12)
    whenever(communityService.prisonerReleased("A5194DY", "MDI", releaseDate)).thenReturn(null)

    service.prisonerReleased("A5194DY", "MDI", releaseDate)

    verify(communityService).prisonerReleased("A5194DY", "MDI", releaseDate)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerNotReleased"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
        assertThat(it["prisonId"]).isEqualTo("MDI")
        assertThat(it["releaseDate"]).isEqualTo(releaseDate.toString())
      },
      isNull()
    )
  }
}
