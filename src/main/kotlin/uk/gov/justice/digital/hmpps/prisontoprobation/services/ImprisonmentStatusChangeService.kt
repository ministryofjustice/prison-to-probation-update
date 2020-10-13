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
class ImprisonmentStatusChangeService(
    private val telemetryClient: TelemetryClient,
    private val offenderService: OffenderService,
    private val communityService: CommunityService,
    private val offenderProbationMatchService: OffenderProbationMatchService,
    @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun validateImprisonmentStatusChange(message: ImprisonmentStatusChangesMessage): MessageResult {
    val (bookingId) = validSignificantStatusChange(message).onIgnore { return Done(it.reason) }
    validSentenceDatesWithStartDate(bookingId).onIgnore { return Done(it.reason) }
    val booking = validActiveBooking(bookingId).onIgnore { return Done(it.reason) }
    validBookingForInterestedPrison(booking).onIgnore { return Done(it.reason) }
    return TryLater(message.bookingId)
  }

  fun processImprisonmentStatusChangeAndUpdateProbation(message: ImprisonmentStatusChangesMessage): MessageResult {
    val (bookingId, imprisonmentStatusSeq) = message
    log.info("Imprisonment status for booking $bookingId has changed")

    val (result, telemetryEvent) = processStatusChange(message)

    telemetryClient.trackEvent(telemetryEvent.name, telemetryEvent.attributes + mapOf(
        "imprisonmentStatusSeq" to imprisonmentStatusSeq.toString(),
        "bookingId" to bookingId.toString()
    ), null)

    return result
  }

  private fun processStatusChange(message: ImprisonmentStatusChangesMessage): Pair<MessageResult, TelemetryEvent> {
    val (bookingId) = getSignificantStatusChange(message).onIgnore { return Done() to it.reason }
    val sentenceDetail = getSentenceDatesWithStartDate(bookingId).onIgnore { return Done() to it.reason }
    val sentenceStartDate = sentenceDetail.sentenceStartDate as LocalDate
    val booking = getActiveBooking(bookingId).onIgnore { return Done() to it.reason }
    val offenderNo = offenderProbationMatchService.ensureOffenderNumberExistsInProbation(booking, sentenceStartDate)
        .onIgnore { return TryLater(bookingId) to it.reason }
    val (bookingNumber, _, _) = getBookingForInterestedPrison(booking).onIgnore { return Done() to it.reason.with(booking).with(sentenceStartDate) }
    updateProbationCustodyBookingNumber(offenderNo, sentenceStartDate, bookingNumber).onIgnore { return TryLater(bookingId) to it.reason.with(booking).with(sentenceStartDate) }
    booking.agencyId?.let {agencyId ->
      updateProbationPrisonLocation(offenderNo, bookingNumber, agencyId).onIgnore { return TryLater(bookingId, sentenceDetail.sentenceExpiryDate) to it.reason.with(booking).with(sentenceStartDate) }
    }
    updateProbationKeyDates(offenderNo, bookingNumber, sentenceDetail).onIgnore { return TryLater(bookingId, sentenceDetail.sentenceExpiryDate) to it.reason.with(booking).with(sentenceStartDate) }

    return Done() to TelemetryEvent("P2PImprisonmentStatusUpdated").with(booking).with(sentenceStartDate)
  }

  private fun updateProbationKeyDates(offenderNo: String, bookingNumber: String, sentenceDetail: SentenceDetail):  Result<Unit, TelemetryEvent> =
    communityService.replaceProbationCustodyKeyDates(offenderNo, bookingNumber, sentenceDetail.asProbationKeyDates())
        ?.let { Success(Unit) }
        ?: Ignore(TelemetryEvent("P2PKeyDatesNotUpdated"))

  private fun updateProbationPrisonLocation(offenderNo: String, bookingNumber: String, agencyId: String):  Result<Unit, TelemetryEvent> =
      communityService.updateProbationCustody(offenderNo, bookingNumber, UpdateCustody(nomsPrisonInstitutionCode = agencyId))
          ?.let { Success(Unit) }
          ?: Ignore(TelemetryEvent("P2PLocationNotUpdated"))

  private fun updateProbationCustodyBookingNumber(offenderNo: String, sentenceStartDate: LocalDate, bookingNumber: String):  Result<Unit, TelemetryEvent> =
    communityService.updateProbationCustodyBookingNumber(offenderNo, UpdateCustodyBookingNumber(sentenceStartDate, bookingNumber))
        ?.let { Success(Unit) }
        ?: Ignore(TelemetryEvent("P2PBookingNumberNotAssigned"))


  private fun validSignificantStatusChange(statusChange: ImprisonmentStatusChangesMessage): Result<ImprisonmentStatusChangesMessage, String> =
  // for each sentence 3 NOMIS events are raised with different sequences, the one with sequence zero happens to be a insert of a new status
  // a ticket (DT-568) has been raised for a trigger change to improve this so only a new single event is raised when conviction status actually changes
      // for now use this to optimise our processing (else we would process a single status change 3 times per imposed sentence)
      if (statusChange.imprisonmentStatusSeq == 0L) Success(statusChange) else Ignore("Non zero sequence")

  private fun getSignificantStatusChange(statusChange: ImprisonmentStatusChangesMessage): Result<ImprisonmentStatusChangesMessage, TelemetryEvent> =
      Success(validSignificantStatusChange(statusChange).onIgnore { return Ignore(TelemetryEvent("P2PImprisonmentStatusNotSequenceZero")) })

  private fun validSentenceDatesWithStartDate(bookingId: Long): Result<SentenceDetail, String> =
      with(offenderService.getSentenceDetail(bookingId)) {
        this.sentenceStartDate
            ?.let { Success(this) }
            ?: Ignore("No sentence start date")
      }

  private fun getSentenceDatesWithStartDate(bookingId: Long): Result<SentenceDetail, TelemetryEvent> =
      Success(validSentenceDatesWithStartDate(bookingId).onIgnore { return Ignore(TelemetryEvent("P2PImprisonmentStatusNoSentenceStartDate")) })

  private fun validActiveBooking(bookingId: Long): Result<Booking, String> =
      offenderService.getBooking(bookingId).takeIf { it.activeFlag }?.let { Success(it) }
          ?: Ignore("Not an active booking")

  private fun getActiveBooking(bookingId: Long): Result<Booking, TelemetryEvent> =
      Success(validActiveBooking(bookingId).onIgnore { return Ignore(TelemetryEvent("P2PImprisonmentStatusIgnored", mapOf("reason" to it.reason))) })

  private fun validBookingForInterestedPrison(booking: Booking): Result<Booking, String> =
      if (isBookingInInterestedPrison(booking.agencyId)) {
        Success(booking)
      } else Ignore("Not at an interested prison")

  private fun getBookingForInterestedPrison(booking: Booking): Result<Booking, TelemetryEvent> =
      Success(validBookingForInterestedPrison(booking).onIgnore { return Ignore(TelemetryEvent("P2PImprisonmentStatusIgnored", mapOf("reason" to it.reason))) })

  private fun isBookingInInterestedPrison(toAgency: String?) =
      allowAnyPrison() || allowedPrisons.contains(toAgency)

  private fun allowAnyPrison() = allowedPrisons.isEmpty()

}


private fun TelemetryEvent.with(sentenceStartDate: LocalDate): TelemetryEvent = TelemetryEvent(this.name, this.attributes + mapOf(
    "sentenceStartDate" to sentenceStartDate.format(DateTimeFormatter.ISO_DATE)
))

