package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import java.time.LocalDateTime

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
        val hmppsDomainEvent = gson.fromJson(message, HMPPSDomainEvent::class.java)
        when (hmppsDomainEvent.additionalInformation.reason) {
          "RECALL" -> {
            releaseAndRecallService.prisonerRecalled(
              hmppsDomainEvent.additionalInformation.nomsNumber,
              LocalDateTime.parse(hmppsDomainEvent.occurredAt).toLocalDate()
            )
          }
        }
      }
      else -> log.info("Received a message wasn't expected $eventType")
    }
  }
}

data class AdditionalInformation(val nomsNumber: String, val reason: String)
data class HMPPSDomainEvent(val occurredAt: String, val additionalInformation: AdditionalInformation)

data class HMPPSEventType(val Value: String)
data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
data class HMPPSMessage(
  val Message: String,
  val MessageId: String,
  val MessageAttributes: HMPPSMessageAttributes
)
