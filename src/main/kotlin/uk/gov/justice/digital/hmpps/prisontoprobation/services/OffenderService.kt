@file:Suppress("DEPRECATION")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

@Service
open class OffenderService(@Qualifier("elite2ApiRestTemplate") val restTemplate: OAuth2RestTemplate) {

    private val prisonerListType = object : ParameterizedTypeReference<List<Prisoner>>() {
    }

    open fun getOffender(offenderNo: String): Prisoner {
        val response = restTemplate.exchange("/api/prisoners?offenderNo={offenderNo}", HttpMethod.GET, null, prisonerListType, offenderNo)
        return response.body!![0]
    }

    open fun getBooking(bookingId: Long): Booking {
        val response = restTemplate.getForEntity("/api/bookings/{bookingId}?basicInfo=true", Booking::class.java, bookingId)
        return response.body!!
    }

    open fun getMovement(bookingId: Long, movementSeq: Long): Movement? {
        return try {
            val response = restTemplate.getForEntity("/api/bookings/{bookingId}/movement/{movementSeq}", Movement::class.java, bookingId, movementSeq)
            response.body!!
        } catch (e : HttpClientErrorException) {
            if (e.statusCode != HttpStatus.NOT_FOUND) throw e
            // 404 is "valid" since it means movement is for an inactive booking
            null
        }
    }
}

data class Prisoner(
        val offenderNo: String,
        val pncNumber: String?,
        val croNumber: String?,
        val firstName: String,
        val middleNames: String?,
        val lastName: String,
        val dateOfBirth: String,
        val currentlyInPrison: String,
        val latestBookingId: Long?,
        val latestLocationId: String?,
        val latestLocation: String?,
        val convictedStatus: String?,
        val imprisonmentStatus: String?,
        val receptionDate: String?
        )

data class Movement(
        val offenderNo: String,
        val createDateTime: LocalDateTime,
        val fromAgency: String?,
        val toAgency: String?,
        val movementType: String,
        val directionCode: String
)

data class Booking(
        val bookingNo: String,
        val activeFlag: Boolean
)