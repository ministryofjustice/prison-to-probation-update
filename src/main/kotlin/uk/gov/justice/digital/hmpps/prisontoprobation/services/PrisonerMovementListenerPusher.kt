package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service


@Service
open class PrisonerMovementListenerPusher(
    private val prisonMovementService: PrisonMovementService,
    private val bookingChangeService: BookingChangeService,
    private val sentenceChangeService: SentenceChangeService
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  @JmsListener(destination = "\${sqs.queue.name}")
  open fun pushPrisonMovementToProbation(requestJson: String?) {
    log.debug(requestJson)
    val (message, messageId, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $messageId type $eventType")

    when (eventType) {
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> prisonMovementService.checkMovementAndUpdateProbation(fromJson(message))
      "COURT_SENTENCE-CHANGED" -> sentenceChangeService.checkSentenceChangeAndUpdateProbation(fromJson(message))
      "OFFENDER_BOOKING-INSERTED" -> bookingChangeService.checkBookingCreationAndUpdateProbation(fromJson(message))
      "OFFENDER_BOOKING-REASSIGNED" -> bookingChangeService.checkBookingReassignedAndUpdateProbation(fromJson(message))
      "BOOKING_NUMBER-CHANGED" -> bookingChangeService.checkBookingNumberChangedAndUpdateProbation(fromJson(message))
      else -> log.warn("We received a message of event type $eventType which I really wasn't expecting")
    }
  }

  private inline fun <reified T> fromJson(message: String): T {
    return gson.fromJson(message, T::class.java)
  }
}

data class EventType(val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
data class ExternalPrisonerMovementMessage(val bookingId: Long, val movementSeq: Long)
data class OffenderBookingInsertedMessage(val bookingId: Long, val offenderId: Long)
data class OffenderBookingReassignedMessage(val bookingId: Long, val offenderId: Long, val previousOffenderId: Long)
data class BookingNumberChangedMessage(val bookingId: Long, val offenderId: Long, val bookingNumber: String, val previousBookingNumber: String)
data class CourtSentenceChangesMessage(val bookingId: Long)

