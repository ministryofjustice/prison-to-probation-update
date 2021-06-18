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
class HMPPSPrisonerChangesListenerPusher(
  private val releaseAndRecallService: ReleaseAndRecallService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  @JmsListener(destination = "#{@'hmpps.sqs-uk.gov.justice.digital.hmpps.prisontoprobation.config.SqsConfigProperties'.hmppsQueue.queueName}", containerFactory = "hmppsJmsListenerContainerFactory")
  fun pushHMPPSPrisonUpdateToProbation(requestJson: String?) {
    log.debug(requestJson)
    val (message, messageId, messageAttributes) = gson.fromJson(requestJson, HMPPSMessage::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $message $messageId type $eventType")

    when (eventType) {
      "prison-offender-events.prisoner.received" -> {
        val offenderMessage = gson.fromJson(message, OffenderMessage::class.java)
        if (offenderMessage.additionalInformation.reason == "RECALL") releaseAndRecallService.prisonerRecalled(offenderMessage)
      }
      else -> log.info("Received a message wasn't expected $eventType")
    }
  }
}

data class OffenderMessage(val version: String, val description: String, val additionalInformation: PrisonerRecalled)

internal data class HMPPSEventType(val Value: String)
internal data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
internal data class HMPPSMessage(val Message: String, val MessageId: String, val MessageAttributes: HMPPSMessageAttributes)
