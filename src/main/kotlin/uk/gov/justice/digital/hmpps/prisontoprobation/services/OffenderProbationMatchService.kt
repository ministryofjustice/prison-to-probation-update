package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success


@Service
class OffenderProbationMatchService(
    private val telemetryClient: TelemetryClient,
    private val offenderSearchService: OffenderSearchService,
    private val offenderService: OffenderService
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun ensureOffenderNumberExistsInProbation(booking: Booking): Result<String, TelemetryEvent> {
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

    // TODO for now just log the match and throw results away
    telemetryClient.trackEvent(
        "P2POffenderMatch",
        mapOf(
            "offenderNo" to booking.offenderNo,
            "bookingNumber" to booking.bookingNo,
            "matches" to result.matches.size.toString(),
            "crns" to result.matches.joinToString { it.offender.otherIds?.crn ?: "unknown" }

        ),
        null
    )

    // TODO we are currently ignoring the response, but shortly we will return an error if we can't match with a
    // probation offender
    return Success(booking.offenderNo)
  }

}