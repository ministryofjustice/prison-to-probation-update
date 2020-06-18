package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service


@Service
class BookingChangeService(private val telemetryClient: TelemetryClient,
                           private val offenderService: OffenderService,
                           private val communityService: CommunityService,
                           @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>
                           ) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }


  fun validateBookingNumberChange(message: BookingNumberChangedMessage): MessageResult {
    val booking = offenderService.getBooking(message.bookingId)

    return if (isBookingInInterestedPrison(booking.agencyId)) {
      val mergedOffenders = offenderService.getMergedIdentifiers(message.bookingId)
      if (mergedOffenders.isEmpty()) {
        Done("No merged offenders found for ${message.bookingId}")
      } else {
        TryLater(message.bookingId)
      }
    } else {
      Done("Not at an interested prison")
    }
  }
  fun processBookingNumberChangedAndUpdateProbation(message: BookingNumberChangedMessage) : MessageResult {
    val (bookingId: Long) = message
    val booking = offenderService.getBooking(bookingId)

    if (isBookingInInterestedPrison(booking.agencyId)) {

      val trackingAttributes = mapOf(
          "bookingId" to bookingId.toString(),
          "offenderNo" to booking.offenderNo)

      val mergedOffenders = offenderService.getMergedIdentifiers(message.bookingId)

      mergedOffenders.forEach {
        val maybeIds = communityService.replaceProbationOffenderNo(it.value, booking.offenderNo)
        maybeIds?.let { ids ->
          telemetryClient.trackEvent("P2PBookingNumberChanged", trackingAttributes + ("oldOffenderNo" to it.value) + ("crn" to ids.crn), null)
        }
            ?: telemetryClient.trackEvent("P2PBookingNumberChangedOffenderNotFound", trackingAttributes + ("oldOffenderNo" to it.value), null)
      }
    }
    return Done("Booking $bookingId has booking number changed")
  }

  private fun isBookingInInterestedPrison(agency: String?) =
      allowAnyPrison() || allowedPrisons.contains(agency)

  private fun allowAnyPrison() = allowedPrisons.isEmpty()

}