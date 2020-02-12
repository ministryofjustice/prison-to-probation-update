
package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
open class ImprisonmentStatusChangeService(
    private val telemetryClient: TelemetryClient
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun checkImprisonmentStatusChangeAndUpdateProbation(message: ImprisonmentStatusChangesMessage) {
    val (bookingId, imprisonmentStatusSeq) = message
    val trackingAttributes = mapOf(
        "bookingId" to bookingId.toString(),
        "imprisonmentStatusSeq" to imprisonmentStatusSeq.toString())

    log.info("Imprisonment status for booking $bookingId has changed")
    telemetryClient.trackEvent("P2PImprisonmentStatusChanged", trackingAttributes, null)
  }

  open fun checkSentenceImposedAndUpdateProbation(message: SentenceImposedMessage) {
    val (offenderNo) = message

    val trackingAttributes = mapOf(
        "offenderNo" to offenderNo)

    log.info("Sentence imposed for offender $offenderNo")
    telemetryClient.trackEvent("P2PSentenceImposed", trackingAttributes, null)
  }

}