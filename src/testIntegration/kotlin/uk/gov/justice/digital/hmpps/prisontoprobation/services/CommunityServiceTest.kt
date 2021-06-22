package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClientResponseException.BadGateway
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success
import java.net.HttpURLConnection.HTTP_BAD_GATEWAY
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.time.LocalDate

class CommunityServiceTest : NoQueueListenerIntegrationTest() {
  @Autowired
  private lateinit var service: CommunityService

  @Nested
  inner class WhenUpdateCustody {

    @Test
    fun `test put custody calls endpoint`() {
      val expectedUpdatedCustody = createUpdatedCustody()
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(expectedUpdatedCustody.asJson())
            .withStatus(HTTP_OK)
        )
      )

      val updateCustody = createUpdateCustody(nomsPrisonInstitutionCode = "MDI")
      val updatedCustody = service.updateProbationCustody("AB123D", "38353A", updateCustody)

      assertThat(updatedCustody).isEqualTo(expectedUpdatedCustody)
      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber/38353A"))
          .withRequestBody(matchingJsonPath("nomsPrisonInstitutionCode", equalTo("MDI")))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `test put custody will be null if conviction not found`() {
      communityMockServer.stubFor(
        put("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber/38353A").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      val updatedCustody = service.updateProbationCustody("AB123D", "38353A", createUpdateCustody())

      assertThat(updatedCustody).isNull()
    }

    @Test
    fun `test put custody will throw exception for other types of http responses`() {
      communityMockServer.stubFor(
        put("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber/38353A").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.updateProbationCustody("AB123D", "38353A", createUpdateCustody()) }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class WhenUpdateCustodyBookingNumber {

    @Test
    fun `test put custody booking number calls endpoint`() {
      val expectedUpdatedCustody = createUpdatedCustody()

      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(expectedUpdatedCustody.asJson())
            .withStatus(HTTP_OK)
        )
      )

      val updatedCustody = service.updateProbationCustodyBookingNumber(
        "AB123D",
        UpdateCustodyBookingNumber(
          sentenceStartDate = LocalDate.now(),
          bookingNumber = "38353A"
        )
      )

      assertThat(updatedCustody).isEqualTo(expectedUpdatedCustody)
      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(matchingJsonPath("bookingNumber", equalTo("38353A")))
          .withRequestBody(matchingJsonPath("sentenceStartDate", matching(".*")))
      )
    }

    @Test
    fun `test custody will be null if not found`() {
      communityMockServer.stubFor(
        put("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      val updatedCustody = service.updateProbationCustodyBookingNumber("AB123D", createUpdatedCustodyBookingNumber())

      assertThat(updatedCustody).isNull()
    }

    @Test
    fun `test will throw exception for other types of http responses`() {
      communityMockServer.stubFor(
        put("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.updateProbationCustodyBookingNumber("AB123D", createUpdatedCustodyBookingNumber()) }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class WhenReplaceProbationCustodyKeyDates {

    @Test
    fun `test post key dates calls endpoint`() {
      val expectedUpdatedCustody = createUpdatedCustody()

      communityMockServer.stubFor(
        post(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(expectedUpdatedCustody.asJson())
            .withStatus(HTTP_OK)
        )
      )

      val replaceCustodyKeyDates = createReplaceCustodyKeyDates()
      val updatedCustody = service.replaceProbationCustodyKeyDates("AB123D", "38353A", replaceCustodyKeyDates)

      assertThat(updatedCustody).isEqualTo(Success(expectedUpdatedCustody))
      communityMockServer.verify(
        postRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/AB123D/bookingNumber/38353A/custody/keyDates"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `test custody will be ignored if not found`() {
      communityMockServer.stubFor(
        post("/secure/offenders/nomsNumber/AB123D/bookingNumber/38353A/custody/keyDates").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found description\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      val updatedCustody = service.replaceProbationCustodyKeyDates("AB123D", "38353A", createReplaceCustodyKeyDates())

      assertThat(updatedCustody).isInstanceOf(Ignore::class.java)
      updatedCustody.onIgnore {
        assertThat(it.reason).contains("not found description")
        return
      }
    }

    @Test
    fun `test will throw exception for other types of http responses`() {
      communityMockServer.stubFor(
        post("/secure/offenders/nomsNumber/AB123D/bookingNumber/38353A/custody/keyDates").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.replaceProbationCustodyKeyDates("AB123D", "38353A", createReplaceCustodyKeyDates()) }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  internal inner class GetConvictions {
    @Test
    fun `test get convictions calls rest endpoint`() {
      val expectedConvictions = listOf(Conviction(index = "1", active = true))

      communityMockServer.stubFor(
        WireMock.get(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(expectedConvictions.asJson())
            .withStatus(HTTP_OK)
        )
      )

      val convictions = service.getConvictions("X153626")

      assertThat(convictions).isEqualTo(expectedConvictions)
      communityMockServer.verify(
        WireMock.getRequestedFor(urlEqualTo("/secure/offenders/crn/X153626/convictions"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }
    @Test
    fun `test can read conviction`() {
      val convictions = service.getConvictions("TEST")

      assertThat(convictions).hasSize(1)
      assertThat(convictions[0].sentence?.startDate).isEqualTo(LocalDate.parse("2013-06-25"))
      assertThat(convictions[0].custody).isNotNull
    }
  }

  @Nested
  internal inner class UpdateProbationOffenderNo {
    @Test
    fun `test updateProbationOffenderNo calls rest endpoint`() {
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(IDs(crn = "X153626").asJson())
            .withStatus(HTTP_OK)
        )
      )

      service.updateProbationOffenderNo("X153626", "AB123D")

      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/crn/X153626/nomsNumber"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(MatchesJsonPathPattern("nomsNumber", equalTo("AB123D")))
      )
    }
  }

  @Nested
  internal inner class ReplaceProbationOffenderNo {
    @Test
    fun `test replaceProbationOffenderNo calls rest endpoint`() {
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(listOf(IDs(crn = "X153626")).asJson())
            .withStatus(HTTP_OK)
        )
      )

      service.replaceProbationOffenderNo("A11111Y", "A99999Y")

      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A11111Y/nomsNumber"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(MatchesJsonPathPattern("nomsNumber", equalTo("A99999Y")))
      )
    }

    @Test
    fun `test replaceProbationOffenderNo will return identifiers of the offender updated`() {
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(listOf(IDs(crn = "X153626", nomsNumber = "A99999Y")).asJson())
            .withStatus(HTTP_OK)
        )
      )

      val ids = service.replaceProbationOffenderNo("A11111Y", "A99999Y")

      assertThat(ids).isNotNull.hasSize(1).contains(IDs(crn = "X153626", nomsNumber = "A99999Y"))
    }

    @Test
    fun `test will consume conflict request error and return nothing`() {
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_CONFLICT)
        )
      )

      val maybeIDs = service.replaceProbationOffenderNo("A11111Y", "A99999Y")

      assertThat(maybeIDs).isNull()
    }

    @Test
    fun `test will consume not found request error and return nothing`() {
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      val maybeIDs = service.replaceProbationOffenderNo("A11111Y", "A99999Y")

      assertThat(maybeIDs).isNull()
    }

    @Test
    fun `test will throw exception for other types of http responses`() {
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_GATEWAY)
        )
      )

      assertThatThrownBy { service.replaceProbationOffenderNo("A11111Y", "A99999Y") }.isInstanceOf(BadGateway::class.java)
    }
  }

  @Nested
  internal inner class PrisonerRecalled {
    @Test
    internal fun `prisoner will be recalled`() {
      val expectedCustody = Custody(Institution("HMP Brixton"), "38339A")
      val occurred = LocalDate.of(2021, 5, 12)

      communityMockServer.stubFor(
        put(anyUrl())
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(expectedCustody.asJson())
              .withStatus(HTTP_OK)
          )
      )

      val custody = service.prisonerRecalled("A5194DY", occurred)

      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A5194DY/recalled"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(MatchesJsonPathPattern("occurred", matching(".*")))
      )
      assertThat(custody).isEqualTo(expectedCustody)
    }

    @Test
    internal fun `prisoner not found`() {
      val occurred = LocalDate.of(2021, 5, 12)
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      val custody = service.prisonerRecalled("A5194DY", occurred)

      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A5194DY/recalled"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(MatchesJsonPathPattern("occurred", matching(".*")))
      )
      assertThat(custody).isNull()
    }

    @Test
    internal fun `prisoner does not have single conviction`() {
      val occurred = LocalDate.of(2021, 5, 12)
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_CONFLICT)
        )
      )

      val custody = service.prisonerRecalled("A5194DY", occurred)

      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A5194DY/recalled"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(MatchesJsonPathPattern("occurred", matching(".*")))
      )
      assertThat(custody).isNull()
    }

    @Test
    fun `will throw exception for other types of http responses`() {
      val occurred = LocalDate.of(2021, 5, 12)
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.prisonerRecalled("A5194DY", occurred) }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  internal inner class PrisonerReleased {
    @Test
    internal fun `prisoner will be released`() {
      val expectedCustody = Custody(Institution("HMP Brixton"), "38339A")
      val occurred = LocalDate.of(2021, 5, 12)

      communityMockServer.stubFor(
        put(anyUrl())
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(expectedCustody.asJson())
              .withStatus(HTTP_OK)
          )
      )

      val custody = service.prisonerReleased("A5194DY", occurred)

      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A5194DY/released"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(MatchesJsonPathPattern("occurred", matching(".*")))
      )
      assertThat(custody).isEqualTo(expectedCustody)
    }

    @Test
    internal fun `prisoner not found`() {
      val occurred = LocalDate.of(2021, 5, 12)
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      val custody = service.prisonerReleased("A5194DY", occurred)

      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A5194DY/released"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(MatchesJsonPathPattern("occurred", matching(".*")))
      )
      assertThat(custody).isNull()
    }

    @Test
    internal fun `prisoner does not have single conviction`() {
      val occurred = LocalDate.of(2021, 5, 12)
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_CONFLICT)
        )
      )

      val custody = service.prisonerReleased("A5194DY", occurred)

      communityMockServer.verify(
        putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A5194DY/released"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(MatchesJsonPathPattern("occurred", matching(".*")))
      )
      assertThat(custody).isNull()
    }

    @Test
    fun `will throw exception for other types of http responses`() {
      val occurred = LocalDate.of(2021, 5, 12)
      communityMockServer.stubFor(
        put(anyUrl()).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.prisonerReleased("A5194DY", occurred) }.isInstanceOf(BadRequest::class.java)
    }
  }

  private fun createUpdatedCustody() = Custody(
    institution = Institution("Doncaster"),
    bookingNumber = "38353A"
  )

  private fun createUpdateCustody(nomsPrisonInstitutionCode: String = "MDI") = UpdateCustody(
    nomsPrisonInstitutionCode = nomsPrisonInstitutionCode
  )

  private fun createUpdatedCustodyBookingNumber() = UpdateCustodyBookingNumber(
    sentenceStartDate = LocalDate.now(),
    bookingNumber = "38353A"
  )

  private fun createReplaceCustodyKeyDates() = ReplaceCustodyKeyDates()
}
