@file:Suppress("DEPRECATION")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime

class OffenderServiceTest {
    private val restTemplate: OAuth2RestTemplate = mock()

    private lateinit var service: OffenderService

    @BeforeEach
    fun before() {
        service = OffenderService(restTemplate)
    }

    @Test
    fun `test get offender calls rest template`() {
        val prisonerListType = object : ParameterizedTypeReference<List<Prisoner>>() {
        }
        val expectedPrisoner = createPrisoner()

        whenever(restTemplate.exchange(anyString(), any(), isNull(), eq(prisonerListType), anyString())).thenReturn(ResponseEntity.ok(listOf(expectedPrisoner)))

        val offender = service.getOffender("AB123D")

        assertThat(offender).isEqualTo(expectedPrisoner)

        verify(restTemplate).exchange("/api/prisoners?offenderNo={offenderNo}", HttpMethod.GET, null, prisonerListType, "AB123D")
    }

    @Test
    fun `test get movement calls rest template`() {
        val expectedMovement = createMovement()
        whenever(restTemplate.getForEntity<Movement>(anyString(), any(), anyLong(), anyLong())).thenReturn(ResponseEntity.ok(expectedMovement))

        val movement = service.getMovement(1234L, 1L)

        assertThat(movement).isEqualTo(expectedMovement)

        verify(restTemplate).getForEntity("/api/bookings/{bookingId}/movement/{movementSeq}", Movement::class.java, 1234L, 1L)
    }

    @Test
    fun `test get movement will be null if not found`() {
        whenever(restTemplate.getForEntity<Movement>(anyString(), any(), anyLong(), anyLong())).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))

        val movement = service.getMovement(1234L, 1L)

        assertThat(movement).isNull()
    }

    @Test
    fun `test get movement will throw exception for other types of http responses`() {
        whenever(restTemplate.getForEntity<Movement>(anyString(), any(), anyLong(), anyLong())).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))

        assertThatThrownBy {  service.getMovement(1234L, 1L) } .isInstanceOf(HttpClientErrorException::class.java)
    }

    @Test
    fun `test get booking calls rest template`() {
        val expectedBooking = createBooking()
        whenever(restTemplate.getForEntity<Booking>(anyString(), any(), anyLong())).thenReturn(ResponseEntity.ok(expectedBooking))

        val booking = service.getBooking(1234L)

        assertThat(booking).isEqualTo(expectedBooking)

        verify(restTemplate).getForEntity("/api/bookings/{bookingId}?basicInfo=true", Booking::class.java, 1234L)
    }

    @Test
    fun `test get sentence detail calls rest template`() {
        val expectedSentenceDetails  = SentenceDetail(sentenceStartDate = LocalDate.now())
        whenever(restTemplate.getForEntity<SentenceDetail>(anyString(), any(), anyLong())).thenReturn(ResponseEntity.ok(expectedSentenceDetails))

        val movement = service.getSentenceDetail(1234L)

        assertThat(movement).isEqualTo(expectedSentenceDetails)

        verify(restTemplate).getForEntity("/api/bookings/{bookingId}/sentenceDetail", SentenceDetail::class.java, 1234L)
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

    private fun createBooking() = Booking(
            bookingNo = "38353A",
            activeFlag = true,
            offenderNo = "A5089DY"
    )
}
