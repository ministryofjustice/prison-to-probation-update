
package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
open class SentenceChangeService(private val telemetryClient: TelemetryClient
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun checkSentenceChangeAndUpdateProbation(message: CourtSentenceChangesMessage) {
    val (bookingId) = message
    val trackingAttributes = mapOf(
        "bookingId" to bookingId.toString())

    log.info("Booking $bookingId has had sentence changed")
    telemetryClient.trackEvent("P2PBookingSentenceChanged", trackingAttributes, null)
  }

}