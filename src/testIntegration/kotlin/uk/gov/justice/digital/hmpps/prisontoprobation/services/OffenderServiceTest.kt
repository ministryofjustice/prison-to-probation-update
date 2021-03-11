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
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.time.LocalDate
import java.time.LocalDateTime

class OffenderServiceTest : NoQueueListenerIntegrationTest() {
  @Autowired
  private lateinit var service: OffenderService

  @Test
  fun `test get offender calls rest endpoint`() {
    val expectedPrisoner = createPrisoner()

    prisonMockServer.stubFor(
      get(anyUrl()).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(listOf(expectedPrisoner).asJson())
          .withStatus(HTTP_OK)
      )
    )

    val offender = service.getOffender("AB123D")

    assertThat(offender).isEqualTo(expectedPrisoner)
    prisonMockServer.verify(
      getRequestedFor(urlEqualTo("/api/prisoners?offenderNo=AB123D"))
        .withHeader("Authorization", equalTo("Bearer ABCDE"))
    )
  }

  @Test
  fun `test get movement calls rest endpoint`() {
    val expectedMovement = createMovement()

    prisonMockServer.stubFor(
      get(anyUrl()).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectedMovement.asJson())
          .withStatus(HTTP_OK)
      )
    )

    val movement = service.getMovement(1234L, 1L)

    assertThat(movement).isEqualTo(expectedMovement)
    prisonMockServer.verify(
      getRequestedFor(urlEqualTo("/api/bookings/1234/movement/1"))
        .withHeader("Authorization", equalTo("Bearer ABCDE"))
    )
  }

  @Test
  fun `test get movement will be null if not found`() {
    prisonMockServer.stubFor(
      get("/api/bookings/1234/movement/1").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("{\"error\": \"not found\"}")
          .withStatus(HTTP_NOT_FOUND)
      )
    )

    val movement = service.getMovement(1234L, 1L)

    assertThat(movement).isNull()
  }

  @Test
  fun `test get movement will throw exception for other types of http responses`() {
    prisonMockServer.stubFor(
      get("/api/bookings/1234/movement/1").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HTTP_BAD_REQUEST)
      )
    )

    assertThatThrownBy { service.getMovement(1234L, 1L) }.isInstanceOf(BadRequest::class.java)
  }

  @Test
  fun `test get booking calls rest endpoint`() {
    val expectedBooking =
      """
{
    "offenderNo": "A1234CV",
    "bookingId": 2679999,
    "bookingNo": "9995D",
    "offenderId": 2999950,
    "rootOffenderId": 2999950,
    "firstName": "JOHN",
    "lastName": "SMITH",
    "dateOfBirth": "1983-11-12",
    "age": 37,
    "activeFlag": true,
    "facialImageId": 7373773,
    "agencyId": "PVI",
    "assignedLivingUnitId": 193939,
    "religion": "Sikh",
    "alertsCodes": [
        "H",
        "X"
    ],
    "activeAlertCount": 1,
    "inactiveAlertCount": 1,
    "alerts": [
        {
            "alertId": 32,
            "alertType": "H",
            "alertTypeDescription": "Self Harm",
            "alertCode": "HA",
            "alertCodeDescription": "ACCT Open (HMPS)",
            "comment": "Thought of Self Harm",
            "dateCreated": "2020-12-12",
            "expired": false,
            "active": true,
            "addedByFirstName": "BOB",
            "addedByLastName": "BOBBY"
        },
        {
            "alertId": 1,
            "alertType": "X",
            "alertTypeDescription": "Security",
            "alertCode": "XNR",
            "alertCodeDescription": "Not For Release",
            "comment": "Alert",
            "dateCreated": "2013-02-05",
            "dateExpires": "2013-07-09",
            "expired": true,
            "active": false,
            "addedByFirstName": "BOB",
            "addedByLastName": "BOBBY",
            "expiredByFirstName": "BOB",
            "expiredByLastName": "BOBBY"
        }
    ],
    "assignedLivingUnit": {
        "agencyId": "PVI",
        "locationId": 73773,
        "description": "B-2-912",
        "agencyName": "Pentonville (HMP)"
    },
    "physicalAttributes": {
        "gender": "Male",
        "raceCode": "A1",
        "ethnicity": "Asian/Asian British: Indian",
        "sexCode": "M"
    },
    "physicalCharacteristics": [
        {
            "type": "HAIR",
            "characteristic": "Hair Colour",
            "detail": "Black"
        }
    ],
    "profileInformation": [
        {
            "type": "YOUTH",
            "question": "Youth Offender?",
            "resultValue": "No"
        }
    ],
    "physicalMarks": [
        {
            "type": "Scar",
            "side": "Front",
            "bodyPart": "Face",
            "comment": "BOTH EYEBROWS"
        }
    ],
    "assessments": [
        {
            "bookingId": 2671944,
            "classificationCode": "STANDARD",
            "classification": "Standard",
            "assessmentCode": "CSR",
            "assessmentDescription": "CSR Rating",
            "cellSharingAlertFlag": true,
            "assessmentDate": "2020-12-03",
            "nextReviewDate": "2021-12-03",
            "assessmentStatus": "A",
            "assessmentSeq": 1
        },
        {
            "bookingId": 2671944,
            "classificationCode": "U",
            "classification": "Unsentenced",
            "assessmentCode": "CATEGORY",
            "assessmentDescription": "Categorisation",
            "cellSharingAlertFlag": false,
            "assessmentDate": "2020-12-03",
            "nextReviewDate": "2021-06-01",
            "assessmentStatus": "A",
            "assessmentSeq": 2
        }
    ],
    "csra": "Standard",
    "category": "Unsentenced",
    "categoryCode": "U",
    "birthPlace": "MOON",
    "birthCountryCode": "IND",
    "inOutStatus": "IN",
    "identifiers": [
        {
            "type": "PNC",
            "value": "12/838396B",
            "offenderNo": "A973737CV",
            "bookingId": 26838383,
            "issuedDate": "2017-02-13",
            "caseloadType": "INST"
        }
    ],
    "personalCareNeeds": [
        {
            "problemType": "DISAB",
            "problemCode": "ND",
            "problemStatus": "ON",
            "problemDescription": "No Disability",
            "commentText": "No disabilities declared at WSI.",
            "startDate": "2013-02-05",
            "endDate": null
        }
    ],
    "sentenceDetail": {
        "bookingId": 8282284
    },
    "offenceHistory": [],
    "sentenceTerms": [],
    "aliases": [],
    "status": "ACTIVE IN",
    "legalStatus": "SENTENCED",
    "recall": false,
    "imprisonmentStatus": "SENT",
    "privilegeSummary": {
        "bookingId": 9535935,
        "iepLevel": "Standard",
        "iepDate": "2020-12-02",
        "iepTime": "2020-12-02T19:31:57",
        "daysSinceReview": 12,
        "iepDetails": []
    },
    "locationDescription": "Pentonville (HMP)"
}      
      """.trimIndent()
    prisonMockServer.stubFor(
      get(anyUrl()).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectedBooking)
          .withStatus(HTTP_OK)
      )
    )

    val booking = service.getBooking(1234L)

    assertThat(booking.bookingNo).isEqualTo("9995D")
    assertThat(booking.activeFlag).isTrue
    assertThat(booking.offenderNo).isEqualTo("A1234CV")
    assertThat(booking.agencyId).isEqualTo("PVI")
    assertThat(booking.locationDescription).isEqualTo("Pentonville (HMP)")
    assertThat(booking.firstName).isEqualTo("JOHN")
    assertThat(booking.lastName).isEqualTo("SMITH")
    assertThat(booking.dateOfBirth).isEqualTo(LocalDate.parse("1983-11-12"))
    assertThat(booking.recall).isFalse
    assertThat(booking.legalStatus).isEqualTo("SENTENCED")

    prisonMockServer.verify(
      getRequestedFor(urlEqualTo("/api/bookings/1234?basicInfo=false&extraInfo=true"))
        .withHeader("Authorization", equalTo("Bearer ABCDE"))
    )
  }

  @Test
  fun `test get sentence detail calls rest endpoint`() {
    val expectedSentenceDetails = SentenceDetail()

    prisonMockServer.stubFor(
      get("/api/bookings/1234/sentenceDetail").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectedSentenceDetails.asJson())
          .withStatus(HTTP_OK)
      )
    )

    val movement = service.getSentenceDetail(1234L)

    assertThat(movement).isEqualTo(expectedSentenceDetails)
    prisonMockServer.verify(
      getRequestedFor(urlEqualTo("/api/bookings/1234/sentenceDetail"))
        .withHeader("Authorization", equalTo("Bearer ABCDE"))
    )
  }

  @Test
  fun `test get merged identifiers calls rest endpoint`() {
    val expectedIdentifiers = listOf(BookingIdentifier(type = "MERGED", value = "A99999Y"))

    prisonMockServer.stubFor(
      get("/api/bookings/1234/identifiers?type=MERGED").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(expectedIdentifiers.asJson())
          .withStatus(HTTP_OK)
      )
    )

    val bookingIdentifiers = service.getMergedIdentifiers(1234L)

    assertThat(bookingIdentifiers).isEqualTo(expectedIdentifiers)
    prisonMockServer.verify(
      getRequestedFor(urlEqualTo("/api/bookings/1234/identifiers?type=MERGED"))
        .withHeader("Authorization", equalTo("Bearer ABCDE"))
    )
  }

  @Test
  fun `test get sentence summary calls rest endpoint`() {
    val sentenceTerms =
      """
      [
          {
              "bookingId": 2606990,
              "sentenceSequence": 61,
              "termSequence": 1,
              "sentenceType": "ADIMP_ORA",
              "sentenceTypeDescription": "ORA CJA03 Standard Determinate Sentence",
              "startDate": "2020-10-10",
              "weeks": 6,
              "lifeSentence": false,
              "caseId": "3032984",
              "sentenceTermCode": "IMP",
              "lineSeq": 2,
              "sentenceStartDate": "2020-10-10"
          },
          {
              "bookingId": 2606990,
              "sentenceSequence": 62,
              "termSequence": 1,
              "consecutiveTo": 61,
              "sentenceType": "ADIMP_ORA",
              "sentenceTypeDescription": "ORA CJA03 Standard Determinate Sentence",
              "startDate": "2020-11-21",
              "weeks": 4,
              "lifeSentence": false,
              "caseId": "3032984",
              "sentenceTermCode": "IMP",
              "lineSeq": 3,
              "sentenceStartDate": "2020-11-21"
          },
          {
              "bookingId": 2606990,
              "sentenceSequence": 69,
              "termSequence": 1,
              "sentenceType": "14FTR_ORA",
              "sentenceTypeDescription": "ORA 14 Day Fixed Term Recall",
              "startDate": "2020-01-13",
              "weeks": 38,
              "lifeSentence": false,
              "caseId": "2944476",
              "sentenceTermCode": "IMP",
              "lineSeq": 4,
              "sentenceStartDate": "2020-01-13"
          }
      ]
      """.trimIndent()

    prisonMockServer.stubFor(
      get("/api/offender-sentences/booking/1234/sentenceTerms").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(sentenceTerms)
          .withStatus(HTTP_OK)
      )
    )

    val sentences = service.getCurrentSentences(1234L)

    assertThat(sentences).containsExactly(
      SentenceSummary(
        startDate = LocalDate.parse("2020-10-10"),
        sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence",
        sentenceSequence = 61
      ),
      SentenceSummary(
        startDate = LocalDate.parse("2020-11-21"),
        sentenceTypeDescription = "ORA CJA03 Standard Determinate Sentence",
        sentenceSequence = 62,
        consecutiveTo = 61
      ),
      SentenceSummary(
        startDate = LocalDate.parse("2020-01-13"),
        sentenceTypeDescription = "ORA 14 Day Fixed Term Recall",
        sentenceSequence = 69
      ),
    )
    prisonMockServer.verify(
      getRequestedFor(urlEqualTo("/api/offender-sentences/booking/1234/sentenceTerms"))
        .withHeader("Authorization", equalTo("Bearer ABCDE"))
    )
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
    receptionDate = ""
  )

  private fun createMovement() = Movement(
    offenderNo = "AB123D",
    createDateTime = LocalDateTime.now(),
    fromAgency = "LEI",
    toAgency = "MDI",
    movementType = "TRN",
    directionCode = "OUT"
  )
}
