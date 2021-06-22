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
    val occurred = LocalDate.of(2021, 5, 12)
    whenever(communityService.prisonerRecalled("A5194DY", occurred)).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))

    service.prisonerRecalled("A5194DY", occurred)

    verify(communityService).prisonerRecalled("A5194DY", occurred)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerRecalled"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
        assertThat(it["occurred"]).isEqualTo(occurred.toString())
      },
      isNull()
    )
  }

  @Test
  internal fun `prisoner not recalled`() {
    val occurred = LocalDate.of(2021, 5, 12)
    whenever(communityService.prisonerRecalled("A5194DY", occurred)).thenReturn(null)

    service.prisonerRecalled("A5194DY", occurred)

    verify(communityService).prisonerRecalled("A5194DY", occurred)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerNotRecalled"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
      },
      isNull()
    )
  }

  @Test
  internal fun `prisoner will be released`() {
    val occurred = LocalDate.of(2021, 5, 12)
    whenever(communityService.prisonerReleased("A5194DY", occurred)).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))

    service.prisonerReleased("A5194DY", occurred)

    verify(communityService).prisonerReleased("A5194DY", occurred)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerReleased"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
        assertThat(it["occurred"]).isEqualTo(occurred.toString())
      },
      isNull()
    )
  }

  @Test
  internal fun `prisoner not released`() {
    val occurred = LocalDate.of(2021, 5, 12)
    whenever(communityService.prisonerReleased("A5194DY", occurred)).thenReturn(null)

    service.prisonerReleased("A5194DY", occurred)

    verify(communityService).prisonerReleased("A5194DY", occurred)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerNotReleased"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
      },
      isNull()
    )
  }
}
