package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Service
open class ImprisonmentStatusChangeService(
    private val telemetryClient: TelemetryClient,
    private val offenderService: OffenderService,
    private val communityService: CommunityService,
    @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun checkImprisonmentStatusChangeAndUpdateProbation(message: ImprisonmentStatusChangesMessage) {
    val (bookingId, imprisonmentStatusSeq) = message
    log.info("Imprisonment status for booking $bookingId has changed")

    val (name, attributes) = processStatusChange(message)

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

  private fun processStatusChange(message: ImprisonmentStatusChangesMessage): TelemetryEvent {
    val (bookingId) = getSignificantStatusChange(message).onIgnore { return it.reason }
    val sentenceStartDate = getSentenceStartDate(bookingId).onIgnore { return it.reason }
    val booking = getActiveBooking(bookingId).onIgnore { return it.reason }
    val (bookingNumber, _, offenderNo) = getBookingForInterestedPrison(booking).onIgnore { return it.reason }

    val trackingAttributes = mapOf("bookingNumber" to bookingNumber, "sentenceStartDate" to sentenceStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE))

    return communityService.updateProbationCustodyBookingNumber(offenderNo, UpdateCustodyBookingNumber(sentenceStartDate, bookingNumber))?.let {
      TelemetryEvent("P2PImprisonmentStatusUpdated", trackingAttributes)
    } ?: TelemetryEvent("P2PImprisonmentStatusRecordNotFound", trackingAttributes)
  }

  private fun getSignificantStatusChange(statusChange: ImprisonmentStatusChangesMessage): Result<ImprisonmentStatusChangesMessage, TelemetryEvent> =
      // for each sentence 3 NOMIS events are raised with different sequences, the one with sequence zero happens to be a insert of a new status
      // a ticket (DT-568) has been raised for a trigger change to improve this so only a new single event is raised when conviction status actually changes
      // for now use this to optimise our processing (else we would process a single status change 3 times per imposed sentence)
      if (statusChange.imprisonmentStatusSeq == 0L) Success(statusChange) else Ignore(TelemetryEvent("P2PImprisonmentStatusNotSequenceZero"))

  private fun getSentenceStartDate(bookingId: Long): Result<LocalDate, TelemetryEvent> {
    val sentenceDetail = offenderService.getSentenceDetail(bookingId)
    return sentenceDetail.sentenceStartDate?.let { Success(it) }
        ?: Ignore(TelemetryEvent("P2PImprisonmentStatusNoSentenceStartDate"))

  }

  private fun getActiveBooking(bookingId: Long): Result<Booking, TelemetryEvent> =
      offenderService.getBooking(bookingId).takeIf { it.activeFlag }?.let { Success(it) }
          ?: Ignore(TelemetryEvent("P2PImprisonmentStatusIgnored", mapOf("reason" to "Not an active booking")))

  private fun getBookingForInterestedPrison(booking: Booking): Result<Booking, TelemetryEvent> =
      if (isBookingInInterestedPrison(booking.agencyId)) { Success(booking) }
          else Ignore(TelemetryEvent("P2PImprisonmentStatusIgnored", mapOf("reason" to "Not at an interested prison")))

  private fun isBookingInInterestedPrison(toAgency: String?) =
      allowAnyPrison() || allowedPrisons.contains(toAgency)

  private fun allowAnyPrison() = allowedPrisons.isEmpty()

}


