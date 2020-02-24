package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
open class BookingChangeService(private val telemetryClient: TelemetryClient
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun checkBookingReassignedAndUpdateProbation(message: OffenderBookingReassignedMessage) {
    val (bookingId, offenderId, previousOffenderId) = message
    val trackingAttributes = mapOf(
        "bookingId" to bookingId.toString(),
        "offenderId" to offenderId.toString(),
        "previousOffenderId" to previousOffenderId.toString())

    log.info("Booking $bookingId reassigned from offender $previousOffenderId to $offenderId")
    telemetryClient.trackEvent("P2PBookingReassigned", trackingAttributes, null)
  }

  open fun checkBookingNumberChangedAndUpdateProbation(message: BookingNumberChangedMessage) {
    val (bookingId: Long, offenderId: Long, bookingNumber: String, previousBookingNumber: String) = message
    val trackingAttributes = mapOf(
        "bookingId" to bookingId.toString(),
        "offenderId" to offenderId.toString(),
        "bookingNumber" to bookingNumber,
        "previousBookingNumber" to previousBookingNumber)

    log.info("Booking $bookingId has booking number changed to $bookingNumber")
    telemetryClient.trackEvent("P2PBookingNumberChanged", trackingAttributes, null)
  }
}