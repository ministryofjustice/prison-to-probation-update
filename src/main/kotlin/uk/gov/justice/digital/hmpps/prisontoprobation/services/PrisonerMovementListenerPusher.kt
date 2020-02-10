package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service


@Service
open class PrisonerMovementListenerPusher(private val prisonMovementService: PrisonMovementService) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  @JmsListener(destination = "\${sqs.queue.name}")
  open fun pushPrisonMovementToProbation(requestJson: String?) {
    log.debug(requestJson)
    val (message, messageId, messageAttributes) = gson.fromJson<Message>(requestJson, Message::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $messageId type ${eventType}")

    when (eventType) {
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> prisonMovementService.checkMovementAndUpdateProbation(gson.fromJson(message, ExternalPrisonerMovementMessage::class.java))
      "COURT_SENTENCE-CHANGED" -> log.info("COURT_SENTENCE-CHANGED ${gson.fromJson(message, CourtSentenceChangesMessage::class.java)}")
      "OFFENDER_BOOKING-INSERTED" -> log.info("OFFENDER_BOOKING-INSERTED ${gson.fromJson(message, OffenderBookingInsertedMessage::class.java)}")
      "OFFENDER_BOOKING-REASSIGNED" -> log.info("OFFENDER_BOOKING-REASSIGNED ${gson.fromJson(message, OffenderBookingReassignedMessage::class.java)}")
      "BOOKING_NUMBER-CHANGED" -> log.info("BOOKING_NUMBER-CHANGED ${gson.fromJson(message, BookingNumberChangedMessage::class.java)}")
      else -> log.warn("We received a message of event type $eventType which I really wasn't expecting")
    }
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

