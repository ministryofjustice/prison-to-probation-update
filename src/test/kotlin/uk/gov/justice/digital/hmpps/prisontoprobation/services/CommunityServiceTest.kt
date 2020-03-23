package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import uk.gov.justice.digital.hmpps.prisontoprobation.services.health.IntegrationTest
import java.net.HttpURLConnection.*
import java.time.LocalDate

class CommunityServiceTest : IntegrationTest() {
  @Autowired
  private lateinit var service: CommunityService
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Nested
  inner class WhenUpdateCustody {

    @Test
    fun `test put custody calls endpoint`() {
      val expectedUpdatedCustody = createUpdatedCustody()
      communityMockServer.stubFor(put(anyUrl()).willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectedUpdatedCustody.asJson())
          .withStatus(HTTP_OK)))

      val updateCustody = createUpdateCustody(nomsPrisonInstitutionCode = "MDI")
      val updatedCustody = service.updateProbationCustody("AB123D", "38353A", updateCustody)

      assertThat(updatedCustody).isEqualTo(expectedUpdatedCustody)
      communityMockServer.verify(putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber/38353A"))
          .withRequestBody(matchingJsonPath("nomsPrisonInstitutionCode", equalTo("MDI")))
          .withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `test put custody will be null if conviction not found`() {
      communityMockServer.stubFor(put("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber/38353A").willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HTTP_NOT_FOUND)))


      val updatedCustody = service.updateProbationCustody("AB123D", "38353A", createUpdateCustody())

      assertThat(updatedCustody).isNull()
    }

    @Test
    fun `test put custody will throw exception for other types of http responses`() {
      communityMockServer.stubFor(put("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber/38353A").willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HTTP_BAD_REQUEST)))

      assertThatThrownBy { service.updateProbationCustody("AB123D", "38353A", createUpdateCustody()) }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class WhenUpdateCustodyBookingNumber {

    @Test
    fun `test put custody booking number calls endpoint`() {
      val expectedUpdatedCustody = createUpdatedCustody()

      communityMockServer.stubFor(put(anyUrl()).willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectedUpdatedCustody.asJson())
          .withStatus(HTTP_OK)))


      val updatedCustody = service.updateProbationCustodyBookingNumber("AB123D", UpdateCustodyBookingNumber(
          sentenceStartDate = LocalDate.now(),
          bookingNumber = "38353A"
      )
      )

      assertThat(updatedCustody).isEqualTo(expectedUpdatedCustody)
      communityMockServer.verify(putRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withRequestBody(matchingJsonPath("bookingNumber", equalTo("38353A")))
          .withRequestBody(matchingJsonPath("sentenceStartDate", matching(".*"))))
    }

    @Test
    fun `test custody will be null if not found`() {
      communityMockServer.stubFor(put("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber").willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HTTP_NOT_FOUND)))

      val updatedCustody = service.updateProbationCustodyBookingNumber("AB123D", createUpdatedCustodyBookingNumber())

      assertThat(updatedCustody).isNull()
    }

    @Test
    fun `test will throw exception for other types of http responses`() {
      communityMockServer.stubFor(put("/secure/offenders/nomsNumber/AB123D/custody/bookingNumber").willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HTTP_BAD_REQUEST)))

      assertThatThrownBy { service.updateProbationCustodyBookingNumber("AB123D", createUpdatedCustodyBookingNumber()) }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class WhenReplaceProbationCustodyKeyDates {

    @Test
    fun `test post key dates calls endpoint`() {
      val expectedUpdatedCustody = createUpdatedCustody()

      communityMockServer.stubFor(post(anyUrl()).willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectedUpdatedCustody.asJson())
          .withStatus(HTTP_OK)))


      val replaceCustodyKeyDates = createReplaceCustodyKeyDates()
      val updatedCustody = service.replaceProbationCustodyKeyDates("AB123D", "38353A", replaceCustodyKeyDates)

      assertThat(updatedCustody).isEqualTo(expectedUpdatedCustody)
      communityMockServer.verify(postRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/AB123D/bookingNumber/38353A/custody/keyDates"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `test custody will be null if not found`() {
      communityMockServer.stubFor(post("/secure/offenders/nomsNumber/AB123D/bookingNumber/38353A/custody/keyDates").willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HTTP_NOT_FOUND)))

      val updatedCustody = service.replaceProbationCustodyKeyDates("AB123D", "38353A", createReplaceCustodyKeyDates())

      assertThat(updatedCustody).isNull()
    }

    @Test
    fun `test will throw exception for other types of http responses`() {
      communityMockServer.stubFor(post("/secure/offenders/nomsNumber/AB123D/bookingNumber/38353A/custody/keyDates").willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HTTP_BAD_REQUEST)))


      assertThatThrownBy { service.replaceProbationCustodyKeyDates("AB123D", "38353A", createReplaceCustodyKeyDates()) }.isInstanceOf(BadRequest::class.java)
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

  private fun Any.asJson() = objectMapper.writeValueAsBytes(this)
}
