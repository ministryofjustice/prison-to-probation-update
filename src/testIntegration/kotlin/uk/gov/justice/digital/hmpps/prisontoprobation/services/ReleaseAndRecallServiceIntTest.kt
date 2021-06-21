package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.github.tomakehurst.wiremock.client.WireMock
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
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import java.net.HttpURLConnection
import java.time.LocalDate

internal class ReleaseAndRecallServiceIntTest : NoQueueListenerIntegrationTest() {
  private val communityService: CommunityService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private lateinit var service: ReleaseAndRecallService

  @BeforeEach
  fun before() {
    service = ReleaseAndRecallService(communityService, telemetryClient)
  }
  @Test
  internal fun `prisoner will be recalled`() {
    val expectedCustody = Custody(Institution("HMP Brixton"), "38339A")

    communityMockServer.stubFor(
      // WireMock.put("/secure/offenders/nomsNumber/A5194DY/recalled").willReturn(
      WireMock.put(WireMock.anyUrl()).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectedCustody.asJson())
          .withStatus(HttpURLConnection.HTTP_OK)
      )
    )

    val occurred = LocalDate.of(2021, 5, 12)

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
    communityMockServer.stubFor(
      WireMock.put("/secure/offenders/nomsNumber/A5194DY/recalled").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpURLConnection.HTTP_NOT_FOUND)
      )
    )

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
}
