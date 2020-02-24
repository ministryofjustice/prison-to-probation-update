package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
open class SentenceDatesChangeService(val telemetryClient: TelemetryClient) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  open fun checkSentenceDateChangeAndUpdateProbation(message: SentenceDatesChangeMessage) {
    val (bookingId: Long) = message
    val trackingAttributes = mapOf(
        "bookingId" to bookingId.toString())

    log.info("Sentence dates have changed for booking $bookingId")
    telemetryClient.trackEvent("P2PSentenceDatesChanged", trackingAttributes, null)
  }
}
