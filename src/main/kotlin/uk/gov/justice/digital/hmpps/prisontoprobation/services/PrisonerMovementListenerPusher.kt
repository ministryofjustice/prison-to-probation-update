package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service


@Service
open class PrisonerMovementListenerPusher(private val offenderService: OffenderService,
                                  private val communityService: CommunityService) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  @JmsListener(destination = "\${sqs.queue.name}")
  open fun pushPrisonMovementToProbation(requestJson: String?) {
    log.debug(requestJson)
    val (Message, MessageId, MessageAttributes) = gson.fromJson<Message>(requestJson, Message::class.java)
    val (bookingId, movementSeq) = gson.fromJson(Message, ExternalPrisonerMovementMessage::class.java)

    log.info("Received message $MessageId type ${MessageAttributes.eventType?.Value} for booking $bookingId with sequence $movementSeq")
  }
}

data class EventType(val Value: String)
data class MessageAttributes(val eventType: EventType?)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
data class ExternalPrisonerMovementMessage(val bookingId: Long, val movementSeq: Long)
