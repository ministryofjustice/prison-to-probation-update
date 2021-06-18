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
    val prisonerRecalled = PrisonerRecalled("A5194DY", "RECALL", "Recall referral date 2021-05-12", "Recall referral date 2021-05-12")
    whenever(communityService.prisonerRecalled("A5194DY", prisonerRecalled)).thenReturn(Custody(Institution("HMP Brixton"), "38339A"))

    service.prisonerRecalled(OffenderMessage("V1.0", "A prisoner has been received into prison", prisonerRecalled))

    verify(communityService).prisonerRecalled("A5194DY", prisonerRecalled)
    verify(telemetryClient).trackEvent(
      eq("P2PPrisonerRecalled"),
      check {
        assertThat(it["nomsNumber"]).isEqualTo("A5194DY")
        assertThat(it["details"]).isEqualTo("Recall referral date 2021-05-12")
      },
      isNull()
    )
  }

  @Test
  internal fun `prisoner not recalled`() {
    val prisonerRecalled = PrisonerRecalled("A5194DY", "RECALL", "Recall referral date 2021-05-12", "Recall referral date 2021-05-12")
    whenever(communityService.prisonerRecalled("A5194DY", prisonerRecalled)).thenReturn(null)

    service.prisonerRecalled(OffenderMessage("V1.0", "A prisoner has been received into prison", prisonerRecalled))

    verify(communityService).prisonerRecalled("A5194DY", prisonerRecalled)
    verify(telemetryClient).trackEvent("P2PPrisonerNotRecalled")
  }
}
