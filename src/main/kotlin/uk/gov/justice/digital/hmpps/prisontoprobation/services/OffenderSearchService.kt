package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate


@Service
class OffenderSearchService(@Qualifier("offenderSearchApiWebClient") private val webClient: WebClient) {
  fun matchProbationOffender(request: MatchRequest): OffenderMatches {
    return webClient.post()
        .uri("/match")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(OffenderMatches::class.java)
        .block()!!
  }
}


data class MatchRequest(
    val firstName: String,
    val surname: String,
    @JsonFormat(pattern="yyyy-MM-dd")
    val dateOfBirth: LocalDate,
    val nomsNumber: String,
    val activeSentence: Boolean = true,
    val pncNumber: String? = null,
    val croNumber: String? = null
    )

data class OffenderMatches(
    val matches: List<OffenderMatch>,
    val matchedBy: String
)

data class OffenderMatch(
    val offender: OffenderDetail
)

data class OffenderDetail(
    val otherIds: IDs,
    val previousSurname: String? = null,
    val title: String? = null,
    val firstName: String? = null,
    val middleNames: List<String>? = null,
    val surname: String? = null,
    val dateOfBirth: LocalDate? = null,
    val gender: String? = null,
    val currentDisposal: String? = null
)

data class IDs(
    val crn: String,
    val pncNumber: String? = null,
    val croNumber: String? = null,
    val niNumber: String? = null,
    val nomsNumber: String? = null,
    val immigrationNumber: String? = null,
    val mostRecentPrisonerNumber: String? = null
)