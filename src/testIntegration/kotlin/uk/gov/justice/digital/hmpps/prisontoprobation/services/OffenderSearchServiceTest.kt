package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisontoprobation.IntegrationTest
import java.net.HttpURLConnection
import java.time.LocalDate

internal class OffenderSearchServiceTest : IntegrationTest() {
  @Autowired
  private lateinit var service: OffenderSearchService

  @Test
  fun `test post match calls rest endpoint`() {
    val expectResult = OffenderMatches(matchedBy = "NOTHING", matches = listOf())

    searchMockServer.stubFor(
      post(anyUrl()).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectResult.asJson())
          .withStatus(HttpURLConnection.HTTP_OK)
      )
    )

    val matches = service.matchProbationOffender(MatchRequest(firstName = "John", surname = "Smith", dateOfBirth = LocalDate.of(1965, 7, 19), nomsNumber = "A12345"))

    assertThat(matches).isEqualTo(expectResult)
    searchMockServer.verify(
      postRequestedFor(urlEqualTo("/match"))
        .withRequestBody(matchingJsonPath("surname", equalTo("Smith")))
        .withRequestBody(matchingJsonPath("firstName", equalTo("John")))
        .withRequestBody(matchingJsonPath("nomsNumber", equalTo("A12345")))
        .withRequestBody(matchingJsonPath("dateOfBirth", equalTo("1965-07-19")))
        .withHeader("Authorization", equalTo("Bearer ABCDE"))
    )
  }
}
