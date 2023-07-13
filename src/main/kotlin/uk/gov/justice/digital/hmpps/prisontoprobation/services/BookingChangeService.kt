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

@Service
class BookingChangeService(
  private val telemetryClient: TelemetryClient,
  private val offenderService: OffenderService,
  private val communityService: CommunityService,
  private val hmppsDomainEventPublisher: HmppsDomainEventPublisher,
  @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>,
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
  fun processBookingNumberChangedAndUpdateProbation(message: BookingNumberChangedMessage): MessageResult {
    val (bookingId: Long) = message
    val booking = offenderService.getBooking(bookingId)

    if (isBookingInInterestedPrison(booking.agencyId)) {
      val trackingAttributes = mapOf(
        "bookingId" to bookingId.toString(),
        "offenderNo" to booking.offenderNo,
      )

      val mergedOffenders = offenderService.getMergedIdentifiers(message.bookingId)

      mergedOffenders.forEach {
        val maybeIds = communityService.replaceProbationOffenderNo(it.value, booking.offenderNo)
        maybeIds?.let { ids ->
          ids.forEach { id ->
            hmppsDomainEventPublisher.publish(
              HmppsDomainEvent(
                eventType = "probation-case.prison-identifier.updated",
                description = "A prison booking number has been changed. The prison number and booking number have been updated on the probation case.",
                personReference = PersonReference(
                  identifiers = listOf(
                    PersonIdentifier("CRN", id.crn),
                    PersonIdentifier("NOMS", booking.offenderNo),
                  ),
                ),
                additionalInformation = mapOf(
                  "bookingNumber" to booking.bookingNo,
                  "previousNomsNumber" to it.value,
                ),
              ),
            )
            telemetryClient.trackEvent("P2PBookingNumberChanged", trackingAttributes + ("oldOffenderNo" to it.value) + ("crn" to id.crn), null)
          }
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
