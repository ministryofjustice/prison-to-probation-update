package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Service
open class ImprisonmentStatusChangeService(
    private val telemetryClient: TelemetryClient,
    private val offenderService: OffenderService
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun checkImprisonmentStatusChangeAndUpdateProbation(message: ImprisonmentStatusChangesMessage) {
    val (bookingId, imprisonmentStatusSeq) = message
    log.info("Imprisonment status for booking $bookingId has changed")

    val (name, attributes) = processStatusChange(bookingId)

    telemetryClient.trackEvent(name, attributes + mapOf(
        "imprisonmentStatusSeq" to imprisonmentStatusSeq.toString(),
        "bookingId" to bookingId.toString()
    ), null)
  }

  open fun checkSentenceImposedAndUpdateProbation(message: SentenceImposedMessage) {
    val (offenderNo) = message

    val trackingAttributes = mapOf(
        "offenderNo" to offenderNo)

    log.info("Sentence imposed for offender $offenderNo")
    telemetryClient.trackEvent("P2PSentenceImposed", trackingAttributes, null)
  }

  private fun processStatusChange(bookingId: Long): TelemetryEvent {
    val sentenceStartDate = getSentenceStartDate(bookingId).onIgnore { return it.reason }
    val bookingNumber = getActiveBookingNumber(bookingId).onIgnore { return it.reason }
    // send to Delius


    return TelemetryEvent("P2PImprisonmentStatusUpdated", mapOf("bookingNumber" to bookingNumber, "sentenceStartDate" to sentenceStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE)))
  }

  private fun getSentenceStartDate(bookingId: Long): Result<LocalDate, TelemetryEvent> {
    val sentenceDetail = offenderService.getSentenceDetail(bookingId)
    return sentenceDetail.sentenceStartDate?.let { Success(it) }
        ?: Ignore(TelemetryEvent("P2PImprisonmentStatusNoSentenceStartDate"))

  }

  private fun getActiveBookingNumber(bookingId: Long): Result<String, TelemetryEvent> =
      offenderService.getBooking(bookingId).takeIf { it.activeFlag }?.let { Success(it.bookingNo) }
          ?: Ignore(TelemetryEvent("P2PImprisonmentStatusIgnored", mapOf("reason" to "Not an active booking")))

}


