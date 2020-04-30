package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SentenceDatesChangeService(
    val telemetryClient: TelemetryClient,
    private val offenderService: OffenderService,
    private val communityService: CommunityService,
    @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun checkSentenceDateChangeAndUpdateProbation(message: SentenceDatesChangeMessage) : MessageResult {
    val (bookingId) = message
    log.info("Sentence dates for booking $bookingId have changed")

    processSentenceDatesChange(message).also {
      telemetryClient.trackEvent(it.name, it.attributes + mapOf(
          "bookingId" to bookingId.toString()
      ), null)
    }

    return Done()
  }

  private fun processSentenceDatesChange(message: SentenceDatesChangeMessage): TelemetryEvent {
    val (bookingId) = message
    val booking = getActiveBooking(bookingId).onIgnore { return it.reason }
    val (bookingNumber, _, offenderNo) = getBookingForInterestedPrison(booking).onIgnore { return it.reason.with(booking) }
    val sentenceDetail = offenderService.getSentenceDetail(bookingId)

    return (communityService.replaceProbationCustodyKeyDates(offenderNo, bookingNumber, sentenceDetail.asProbationKeyDates())
        ?.let { "P2PSentenceDatesChanged" } ?: "P2PSentenceDatesRecordNotFound")
        .let { TelemetryEvent(it).with(booking).with(sentenceDetail) }
  }

  private fun getActiveBooking(bookingId: Long): Result<Booking, TelemetryEvent> =
      offenderService.getBooking(bookingId).takeIf { it.activeFlag }?.let { Result.Success(it) }
          ?: Result.Ignore(TelemetryEvent("P2PSentenceDatesChangeIgnored", mapOf("reason" to "Not an active booking")))

  private fun getBookingForInterestedPrison(booking: Booking): Result<Booking, TelemetryEvent> =
      if (isBookingInInterestedPrison(booking.agencyId)) {
        Result.Success(booking)
      } else Result.Ignore(TelemetryEvent("P2PSentenceDatesChangeIgnored", mapOf("reason" to "Not at an interested prison")))

  private fun isBookingInInterestedPrison(toAgency: String?) =
      allowAnyPrison() || allowedPrisons.contains(toAgency)

  private fun allowAnyPrison() = allowedPrisons.isEmpty()

}

fun SentenceDetail.asProbationKeyDates(): ReplaceCustodyKeyDates = ReplaceCustodyKeyDates(
    conditionalReleaseDate = conditionalReleaseOverrideDate ?: this.conditionalReleaseDate,
    sentenceExpiryDate = sentenceExpiryDate,
    paroleEligibilityDate = paroleEligibilityDate,
    licenceExpiryDate = licenceExpiryDate,
    expectedReleaseDate = confirmedReleaseDate,
    hdcEligibilityDate = homeDetentionCurfewEligibilityDate,
    postSentenceSupervisionEndDate = topupSupervisionExpiryDate
)