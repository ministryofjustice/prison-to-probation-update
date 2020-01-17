@file:Suppress("DEPRECATION")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import java.time.LocalDateTime

class OffenderServiceTest {
    private val restTemplate: OAuth2RestTemplate = mock()

    private lateinit var service: OffenderService

    @Before
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
