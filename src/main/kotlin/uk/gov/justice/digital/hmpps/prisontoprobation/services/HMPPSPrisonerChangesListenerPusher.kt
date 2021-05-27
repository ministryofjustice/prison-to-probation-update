package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service

@Service
@Profile("!no-queue-listener")
class HMPPSPrisonerChangesListenerPusher() {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  @JmsListener(destination = "\${sqs.hmpps.queue.name}", containerFactory = "hmppsJmsListenerContainerFactory")
  fun pushHMPPSPrisonUpdateToProbation(requestJson: String?) {
    log.debug(requestJson)
    val (message, messageId, messageAttributes) = gson.fromJson(requestJson, HMPPSMessage::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $message $messageId type $eventType")
  }
}

internal data class HMPPSEventType(val Value: String)
internal data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
internal data class HMPPSMessage(val Message: String, val MessageId: String, val MessageAttributes: HMPPSMessageAttributes)
