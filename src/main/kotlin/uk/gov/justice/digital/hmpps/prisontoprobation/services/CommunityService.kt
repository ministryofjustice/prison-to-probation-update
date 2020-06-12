package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.LocalDate

@Service
class CommunityService(@Qualifier("probationApiWebClient") private val webClient: WebClient) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val convictionListType = object : ParameterizedTypeReference<List<Conviction>>() {}

  fun updateProbationCustody(offenderNo: String, bookingNo: String, updateCustody: UpdateCustody): Custody? {
    return webClient.put()
        .uri("/secure/offenders/nomsNumber/$offenderNo/custody/bookingNumber/$bookingNo")
        .bodyValue(updateCustody)
        .retrieve()
        .bodyToMono(Custody::class.java)
        .onErrorResume(WebClientResponseException::class.java) { emptyWhen404(it) }
        .block()
  }

  fun updateProbationCustodyBookingNumber(offenderNo: String, updateCustodyBookingNumber: UpdateCustodyBookingNumber): Custody? {
    return webClient.put()
        .uri("/secure/offenders/nomsNumber/$offenderNo/custody/bookingNumber")
        .bodyValue(updateCustodyBookingNumber)
        .retrieve()
        .bodyToMono(Custody::class.java)
        .onErrorResume(WebClientResponseException::class.java) { emptyWhen404(it) }
        .block()
  }

  fun replaceProbationCustodyKeyDates(offenderNo: String, bookingNo: String, replaceCustodyKeyDates: ReplaceCustodyKeyDates): Custody? {
    return webClient.post()
        .uri("/secure/offenders/nomsNumber/$offenderNo/bookingNumber/$bookingNo/custody/keyDates")
        .bodyValue(replaceCustodyKeyDates)
        .retrieve()
        .bodyToMono(Custody::class.java)
        .onErrorResume(WebClientResponseException::class.java) { emptyWhen404(it) }
        .block()
  }
  fun <T> emptyWhen404(exception: WebClientResponseException): Mono<T> =
      if (exception.rawStatusCode == 404) Mono.empty() else Mono.error(exception)
  fun <T> emptyWhen400(exception: WebClientResponseException): Mono<T> =
      if (exception.rawStatusCode == 400) Mono.empty() else Mono.error(exception)

  fun getConvictions(crn: String): List<Conviction> {
    return webClient.get()
        .uri("/secure/offenders/crn/${crn}/convictions")
        .retrieve()
        .bodyToMono(convictionListType)
        .block()!!
  }

  fun updateProbationOffenderNo(crn: String, offenderNo: String): IDs {
    return webClient.put()
        .uri("/secure/offenders/crn/$crn/nomsNumber")
        .bodyValue(UpdateOffenderNomsNumber(nomsNumber = offenderNo))
        .retrieve()
        .bodyToMono(IDs::class.java)
        .block()!!
  }

  fun replaceProbationOffenderNo(oldOffenderNo: String, newOffenderNo: String) : IDs?{
    return webClient.put()
        .uri("/secure/offenders/nomsNumber/$oldOffenderNo/nomsNumber")
        .bodyValue(UpdateOffenderNomsNumber(nomsNumber = newOffenderNo))
        .retrieve()
        .bodyToMono(IDs::class.java)
        .onErrorResume(WebClientResponseException::class.java) { emptyWhen404(it) }
        .onErrorResume(WebClientResponseException::class.java) { emptyWhen400(it) }
        .block()
  }
}

data class UpdateOffenderNomsNumber(
    val nomsNumber: String
)

data class UpdateCustody(
    val nomsPrisonInstitutionCode: String
)

data class Institution(
    val description: String?
)

data class Custody(
    val institution: Institution?,
    val bookingNumber: String?
)

data class UpdateCustodyBookingNumber(
    val sentenceStartDate: LocalDate,
    val bookingNumber: String
)

data class ReplaceCustodyKeyDates(
    val conditionalReleaseDate: LocalDate? = null,
    val licenceExpiryDate: LocalDate? = null,
    val hdcEligibilityDate: LocalDate? = null,
    val paroleEligibilityDate: LocalDate? = null,
    val sentenceExpiryDate: LocalDate? = null,
    val expectedReleaseDate: LocalDate? = null,
    val postSentenceSupervisionEndDate: LocalDate? = null
)

data class Conviction(val index: String, val active: Boolean, val sentence: Sentence? = null, val custody: Custody? = null)

data class Sentence(val startDate: LocalDate?)