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
    val (Message, MessageId) = gson.fromJson<Message>(requestJson, Message::class.java)
    val (bookingId, eventType) = gson.fromJson(Message, PrisonMovementMessage::class.java)

    log.info("Received message {} type {} for booking {}", MessageId, eventType, bookingId)
    // call offender service
    // call community service
  }
}

data class Message(val Message: String, val MessageId: String)
data class PrisonMovementMessage(val bookingId: Long, val eventType: String)
