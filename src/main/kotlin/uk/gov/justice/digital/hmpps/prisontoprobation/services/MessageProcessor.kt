package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class MessageProcessor(
    private val prisonMovementService: PrisonMovementService,
    private val bookingChangeService: BookingChangeService,
    private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService,
    private val sentenceDatesChangeService: SentenceDatesChangeService

) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  fun processMessage(eventType: String, message: String): MessageResult {
    log.debug("Processing message $eventType by $this")
    return when (eventType) {
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> prisonMovementService.checkMovementAndUpdateProbation(fromJson(message))
      "IMPRISONMENT_STATUS-CHANGED" -> imprisonmentStatusChangeService.checkImprisonmentStatusChangeAndUpdateProbation(fromJson(message))
      "BOOKING_NUMBER-CHANGED" -> bookingChangeService.checkBookingNumberChangedAndUpdateProbation(fromJson(message))
      "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED" -> sentenceDatesChangeService.checkSentenceDateChangeAndUpdateProbation(fromJson(message))
      else -> {
        Done("We received a message of event type $eventType which I really wasn't expecting")
      }
    }
  }

  private inline fun <reified T> fromJson(message: String): T {
    return gson.fromJson(message, T::class.java)
  }
}

data class ExternalPrisonerMovementMessage(val bookingId: Long, val movementSeq: Long)
data class BookingNumberChangedMessage(val bookingId: Long, val offenderId: Long, val bookingNumber: String, val previousBookingNumber: String)
data class ImprisonmentStatusChangesMessage(val bookingId: Long, val imprisonmentStatusSeq: Long)
data class SentenceKeyDateChangeMessage(val bookingId: Long)

sealed class MessageResult
class RetryLater(val bookingId: Long) : MessageResult()
class Done(val message: String? = null) : MessageResult()
