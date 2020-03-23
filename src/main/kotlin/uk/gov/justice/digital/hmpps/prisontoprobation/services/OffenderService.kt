package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class OffenderService(@Qualifier("prisonApiWebClient") private val webClient: WebClient) {

  private val prisonerListType = object : ParameterizedTypeReference<List<Prisoner>>() {
  }

  fun getOffender(offenderNo: String): Prisoner {
    return webClient.get()
        .uri("/api/prisoners?offenderNo=$offenderNo")
        .retrieve()
        .bodyToMono(prisonerListType)
        .block()!![0]
  }

  fun getBooking(bookingId: Long): Booking {
    return webClient.get()
        .uri("/api/bookings/$bookingId?basicInfo=true")
        .retrieve()
        .bodyToMono(Booking::class.java)
        .block()!!
  }

  fun getSentenceDetail(bookingId: Long): SentenceDetail {
    return webClient.get()
        .uri("/api/bookings/$bookingId/sentenceDetail")
        .retrieve()
        .bodyToMono(SentenceDetail::class.java)
        .block()!!
  }

  fun getMovement(bookingId: Long, movementSeq: Long): Movement? {
    return webClient.get()
        .uri("/api/bookings/$bookingId/movement/$movementSeq")
        .retrieve()
        .onStatus({ it == HttpStatus.NOT_FOUND }, { Mono.empty() })
        .bodyToMono(Movement::class.java)
        .block()
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
    val activeFlag: Boolean,
    val offenderNo: String,
    val agencyId: String? = null,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate

)

data class SentenceDetail(
    val sentenceStartDate: LocalDate? = null,
    val confirmedReleaseDate: LocalDate? = null,
    val conditionalReleaseDate: LocalDate? = null,
    val conditionalReleaseOverrideDate: LocalDate? = null,
    val licenceExpiryDate: LocalDate? = null,
    val paroleEligibilityDate: LocalDate? = null,
    val sentenceExpiryDate: LocalDate? = null,
    val topupSupervisionExpiryDate: LocalDate? = null,
    val releaseDate: LocalDate? = null,
    val homeDetentionCurfewEligibilityDate: LocalDate? = null
)