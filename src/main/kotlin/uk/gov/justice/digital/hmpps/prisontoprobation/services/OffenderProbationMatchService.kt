package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS


@Service
class OffenderProbationMatchService(
    private val telemetryClient: TelemetryClient,
    private val offenderSearchService: OffenderSearchService,
    private val offenderService: OffenderService,
    private val communityService: CommunityService,
    @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun ensureOffenderNumberExistsInProbation(booking: Booking, sentenceStartDate: LocalDate): Result<String, TelemetryEvent> {
    val prisoner = offenderService.getOffender(booking.offenderNo)

    val result = offenderSearchService.matchProbationOffender(MatchRequest(
        firstName = booking.firstName,
        surname = booking.lastName,
        dateOfBirth = booking.dateOfBirth,
        nomsNumber = booking.offenderNo,
        croNumber = prisoner.croNumber,
        pncNumber = prisoner.pncNumber,
        activeSentence = true
    ))

    log.debug("${booking.offenderNo} matched ${result.matches.size} offender(s)")

    // filter by those with matching sentence
    val filteredCRNs = offendersWithMatchingSentenceDates(result, sentenceStartDate)

    telemetryClient.trackEvent(
        "P2POffenderMatch",
        mapOf(
            "offenderNo" to booking.offenderNo,
            "bookingNumber" to booking.bookingNo,
            "matchedBy" to result.matchedBy,
            "matches" to result.matches.size.toString(),
            "filtered_matches" to filteredCRNs.size.toString(),
            "crns" to result.CRNs(),
            "filtered_crns" to filteredCRNs.sorted().joinToString()
        ),
        null
    )

    // TODO remove this once we gathered enough data proving algorithm works
    doMatchAnalysis(booking, prisoner, sentenceStartDate, filteredCRNs, result)

    return when (result.matchedBy) {
      "ALL_SUPPLIED", "HMPPS_KEY" -> Success(booking.offenderNo) // NOMS number is already set in probation
      else -> {
        when (filteredCRNs.size) {
          0 -> Ignore(TelemetryEvent(name = "P2POffenderNoMatch", attributes = mapOf("offenderNo" to booking.offenderNo, "crns" to result.CRNs())))
          1 -> updateProbationWithOffenderNo(booking, filteredCRNs.first())
          else -> Ignore(TelemetryEvent(name = "P2POffenderTooManyMatches", attributes = mapOf("offenderNo" to booking.offenderNo, "filtered_crns" to filteredCRNs.sorted().joinToString())))
        }
      }
    }
  }

  private fun updateProbationWithOffenderNo(booking: Booking, crn: String): Result<String, TelemetryEvent> {
    return if (isBookingInInterestedPrison(booking.agencyId)) {
      communityService.updateProbationOffenderNo(crn, booking.offenderNo)
      telemetryClient.trackEvent(
          "P2POffenderNumberSet",
          mapOf(
              "offenderNo" to booking.offenderNo,
              "bookingNumber" to booking.bookingNo,
              "crn" to crn
          ),
          null
      )
      Success(booking.offenderNo)
    } else {
      Ignore(TelemetryEvent("P2PChangeIgnored", mapOf("reason" to "Not at an interested prison")))
    }
  }

  private fun doMatchAnalysis(booking: Booking, prisoner: Prisoner, sentenceStartDate: LocalDate, filteredCRNs: Set<String>, result: OffenderMatches) {
    // Now do search without NOMS number to see if we would have got a match, and if so was it was the same match
    // this is only to build up some analysis to  test how effective the matching aalgorithmis
    val sample = offenderSearchService.matchProbationOffender(MatchRequest(
        firstName = booking.firstName,
        surname = booking.lastName,
        dateOfBirth = booking.dateOfBirth,
        nomsNumber = "",
        croNumber = prisoner.croNumber,
        pncNumber = prisoner.pncNumber,
        activeSentence = true
    ))

    // filter by those with matching sentence
    val sampleFilteredCRNs = offendersWithMatchingSentenceDates(sample, sentenceStartDate)


    if (sampleFilteredCRNs == filteredCRNs) {
      telemetryClient.trackEvent(
          "P2POffenderPerfectMatch",
          mapOf(
              "offenderNo" to booking.offenderNo,
              "bookingNumber" to booking.bookingNo,
              "matchedBy" to sample.matchedBy,
              "matches" to sample.matches.size.toString(),
              "filtered_matches" to sampleFilteredCRNs.size.toString(),
              "crns" to sample.CRNs(),
              "filtered_crns" to filteredCRNs.sorted().joinToString()
          ),
          null
      )
    } else {
      telemetryClient.trackEvent(
          "P2POffenderImperfectMatch",
          mapOf(
              "offenderNo" to booking.offenderNo,
              "bookingNumber" to booking.bookingNo,
              "matches" to sample.matches.size.toString(),
              "matchedBy" to sample.matchedBy,
              "crns" to (sample.CRNList().intersect(result.CRNList())).joinToString(),
              "extra_crns" to (sample.CRNList() - result.CRNList()).joinToString(),
              "missing_crns" to (result.CRNList() - sample.CRNList()).joinToString(),
              "extra_filtered_crns" to (sampleFilteredCRNs - filteredCRNs).joinToString(),
              "missing_filtered_crns" to (filteredCRNs - sampleFilteredCRNs).joinToString()
          ),
          null
      )
    }
  }

  private fun offendersWithMatchingSentenceDates(result: OffenderMatches, sentenceStartDate: LocalDate): Set<String> {
    return result.matches
        .map { it.offender.otherIds.crn }
        .map { crn -> crn to custodySentenceDates(crn) }
        .toMap()
        .filter { (_, dates) -> dates.any { it.closeTo(sentenceStartDate) } }
        .keys
  }

  private fun custodySentenceDates(crn: String): List<LocalDate> {
    return communityService.getConvictions(crn).asSequence()
        .filter { conviction -> conviction.custody?.let { true } ?: false }
        .map { conviction -> conviction.sentence }
        .filterNotNull()
        .map { it.startDate }
        .filterNotNull()
        .toList()
  }

  private fun isBookingInInterestedPrison(toAgency: String?) =
      allowAnyPrison() || allowedPrisons.contains(toAgency)

  private fun allowAnyPrison() = allowedPrisons.isEmpty()
}

private fun LocalDate.closeTo(date: LocalDate, days: Int = 7): Boolean = Math.abs(DAYS.between(this, date)) <= days

private fun OffenderMatches.CRNs() = this.CRNList().joinToString()
private fun OffenderMatches.CRNList() = this.matches.map { it.offender.otherIds.crn }.sorted()