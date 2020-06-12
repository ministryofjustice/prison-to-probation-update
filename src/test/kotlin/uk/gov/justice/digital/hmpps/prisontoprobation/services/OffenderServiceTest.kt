@file:Suppress("DEPRECATION")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import uk.gov.justice.digital.hmpps.prisontoprobation.services.health.IntegrationTest
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.time.LocalDateTime

class OffenderServiceTest : IntegrationTest() {
  @Autowired
  private lateinit var service: OffenderService
  @Test
  fun `test get offender calls rest endpoint`() {
    val expectedPrisoner = createPrisoner()

    elite2MockServer.stubFor(get(anyUrl()).willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(listOf(expectedPrisoner).asJson())
        .withStatus(HTTP_OK)))


    val offender = service.getOffender("AB123D")

    assertThat(offender).isEqualTo(expectedPrisoner)
    elite2MockServer.verify(getRequestedFor(urlEqualTo("/api/prisoners?offenderNo=AB123D"))
        .withHeader("Authorization", equalTo("Bearer ABCDE")))
  }

  @Test
  fun `test get movement calls rest endpoint`() {
    val expectedMovement = createMovement()

    elite2MockServer.stubFor(get(anyUrl()).willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(expectedMovement.asJson())
        .withStatus(HTTP_OK)))

    val movement = service.getMovement(1234L, 1L)

    assertThat(movement).isEqualTo(expectedMovement)
    elite2MockServer.verify(getRequestedFor(urlEqualTo("/api/bookings/1234/movement/1"))
        .withHeader("Authorization", equalTo("Bearer ABCDE")))
  }

  @Test
  fun `test get movement will be null if not found`() {
    elite2MockServer.stubFor(get("/api/bookings/1234/movement/1").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(HTTP_NOT_FOUND)))

    val movement = service.getMovement(1234L, 1L)

    assertThat(movement).isNull()
  }

  @Test
  fun `test get movement will throw exception for other types of http responses`() {
    elite2MockServer.stubFor(get("/api/bookings/1234/movement/1").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(HTTP_BAD_REQUEST)))


    assertThatThrownBy { service.getMovement(1234L, 1L) }.isInstanceOf(BadRequest::class.java)
  }

  @Test
  fun `test get booking calls rest endpoint`() {
    val expectedBooking = createBooking()
    elite2MockServer.stubFor(get(anyUrl()).willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(expectedBooking.asJson())
        .withStatus(HTTP_OK)))

    val booking = service.getBooking(1234L)

    assertThat(booking).isEqualTo(expectedBooking)
    elite2MockServer.verify(getRequestedFor(urlEqualTo("/api/bookings/1234?basicInfo=true"))
        .withHeader("Authorization", equalTo("Bearer ABCDE")))
  }

  @Test
  fun `test get sentence detail calls rest endpoint`() {
    val expectedSentenceDetails = SentenceDetail()

    elite2MockServer.stubFor(get("/api/bookings/1234/sentenceDetail").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(expectedSentenceDetails.asJson())
        .withStatus(HTTP_OK)))


    val movement = service.getSentenceDetail(1234L)

    assertThat(movement).isEqualTo(expectedSentenceDetails)
    elite2MockServer.verify(getRequestedFor(urlEqualTo("/api/bookings/1234/sentenceDetail"))
        .withHeader("Authorization", equalTo("Bearer ABCDE")))

  }

  @Test
  fun `test get merged identifiers calls rest endpoint`() {
      val expectedIdentifiers = listOf(BookingIdentifier(type = "MERGED", value = "A99999Y"))

    elite2MockServer.stubFor(get("/api/bookings/1234/identifiers?type=MERGED").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(expectedIdentifiers.asJson())
        .withStatus(HTTP_OK)))


    val bookingIdentifiers = service.getMergedIdentifiers(1234L)

    assertThat(bookingIdentifiers).isEqualTo(expectedIdentifiers)
    elite2MockServer.verify(getRequestedFor(urlEqualTo("/api/bookings/1234/identifiers?type=MERGED"))
        .withHeader("Authorization", equalTo("Bearer ABCDE")))

  }


  private fun createPrisoner() = Prisoner(
      offenderNo = "AB123D",
      pncNumber = "",
      croNumber = "",
      firstName = "",
      middleNames = "",
      lastName = "",
      dateOfBirth = "",
      currentlyInPrison = "",
      latestBookingId = 1L,
      latestLocationId = "",
      latestLocation = "",
      convictedStatus = "",
      imprisonmentStatus = "",
      receptionDate = "")

  private fun createMovement() = Movement(
      offenderNo = "AB123D",
      createDateTime = LocalDateTime.now(),
      fromAgency = "LEI",
      toAgency = "MDI",
      movementType = "TRN",
      directionCode = "OUT"
  )

}
