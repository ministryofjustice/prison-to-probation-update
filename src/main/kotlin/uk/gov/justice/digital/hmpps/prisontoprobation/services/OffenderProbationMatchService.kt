@file:Suppress("FunctionName")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.notifications.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.prisontoprobation.notifications.HmppsDomainEventPublisher
import uk.gov.justice.digital.hmpps.prisontoprobation.notifications.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisontoprobation.notifications.PersonReference
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.abs

typealias OffenderIdentifiers = Pair<String, String>

typealias FailureResult = Pair<TelemetryEvent, SynchroniseStatus>

@Service
class OffenderProbationMatchService(
  private val telemetryClient: TelemetryClient,
  private val offenderSearchService: OffenderSearchService,
  private val offenderService: OffenderService,
  private val communityService: CommunityService,
  private val hmppsDomainEventPublisher: HmppsDomainEventPublisher,
  @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun ensureOffenderNumberExistsInProbation(
    booking: Booking,
    sentenceStartDate: LocalDate,
  ): Result<OffenderIdentifiers, FailureResult> {
    val prisoner = offenderService.getOffender(booking.offenderNo)

    val result = offenderSearchService.matchProbationOffender(
      MatchRequest(
        firstName = booking.firstName,
        surname = booking.lastName,
        dateOfBirth = booking.dateOfBirth,
        nomsNumber = booking.offenderNo,
        croNumber = prisoner.croNumber,
        pncNumber = prisoner.pncNumber,
        activeSentence = true,
      ),
    )

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
        "filtered_crns" to filteredCRNs.sorted().joinToString(),
      ),
      null,
    )

    return when (result.matchedBy) {
      "ALL_SUPPLIED", "ALL_SUPPLIED_ALIAS", "HMPPS_KEY" -> Success(
        booking.offenderNo to result.CRNList().first(),
      ) // NOMS number is already set in probation
      else -> {
        when (filteredCRNs.size) {
          0 -> ignoreNotMatch(booking, result)
          1 -> updateProbationWithOffenderNo(booking, filteredCRNs.first())
          else -> ignoreTooManyMatches(booking, filteredCRNs)
        }
      }
    }
  }

  private fun ignoreTooManyMatches(
    booking: Booking,
    filteredCRNs: Set<String>,
  ) = Ignore(
    TelemetryEvent(
      name = "P2POffenderTooManyMatches",
      attributes = mapOf(
        "offenderNo" to booking.offenderNo,
        "filtered_crns" to filteredCRNs.sorted().joinToString(),
      ),
    ) to SynchroniseStatus(
      matchingCrns = filteredCRNs.sorted().joinToString(),
      state = SynchroniseState.TOO_MANY_MATCHES,
    ),
  )

  private fun ignoreNotMatch(
    booking: Booking,
    result: OffenderMatches,
  ) = Ignore(
    TelemetryEvent(
      name = "P2POffenderNoMatch",
      attributes = mapOf("offenderNo" to booking.offenderNo, "crns" to result.CRNs()),
    ) to SynchroniseStatus(
      matchingCrns = result.CRNs(),
      state = if (result.CRNList()
          .isEmpty()
      ) {
        SynchroniseState.NO_MATCH
      } else {
        SynchroniseState.NO_MATCH_WITH_SENTENCE_DATE
      },
    ),
  )

  private fun updateProbationWithOffenderNo(
    booking: Booking,
    crn: String,
  ): Result<OffenderIdentifiers, FailureResult> {
    return if (isBookingInInterestedPrison(booking.agencyId)) {
      communityService.updateProbationOffenderNo(crn, booking.offenderNo)
      telemetryClient.trackEvent(
        "P2POffenderNumberSet",
        mapOf(
          "offenderNo" to booking.offenderNo,
          "bookingNumber" to booking.bookingNo,
          "crn" to crn,
        ),
        null,
      )
      hmppsDomainEventPublisher.publish(
        HmppsDomainEvent(
          eventType = "probation-case.prison-identifier.added",
          description = "A probation case has been matched with a booking in the prison system. The prison number and booking number have been added to the probation case.",
          personReference = PersonReference(
            identifiers = listOf(
              PersonIdentifier("CRN", crn),
              PersonIdentifier("NOMS", booking.offenderNo),
            ),
          ),
          additionalInformation = mapOf("bookingNumber" to booking.bookingNo),
        ),
      )
      Success(booking.offenderNo to crn)
    } else {
      Ignore(
        TelemetryEvent("P2PChangeIgnored", mapOf("reason" to "Not at an interested prison")) to SynchroniseStatus(
          state = SynchroniseState.NOT_VALID,
        ),
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

private fun LocalDate.closeTo(date: LocalDate, days: Int = 7): Boolean = abs(DAYS.between(this, date)) <= days

private fun OffenderMatches.CRNs() = this.CRNList().joinToString()
private fun OffenderMatches.CRNList() = this.matches.map { it.offender.otherIds.crn }.sorted()
