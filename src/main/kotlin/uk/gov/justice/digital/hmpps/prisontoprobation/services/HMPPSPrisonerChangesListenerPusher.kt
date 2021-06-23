package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.fasterxml.jackson.databind.ObjectMapper
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
  private val objectMapper: ObjectMapper
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "#{@'hmpps.sqs-uk.gov.justice.digital.hmpps.prisontoprobation.config.SqsConfigProperties'.queues['hmppsDomainEventQueue'].queueName}", containerFactory = "hmppsJmsListenerContainerFactory")
  fun pushHMPPSPrisonUpdateToProbation(requestJson: String?) {
    log.debug(requestJson)
    val (message, messageId, messageAttributes) = objectMapper.readValue(requestJson, HMPPSMessage::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $message $messageId type $eventType")

    when (eventType) {
      "prison-offender-events.prisoner.received" -> {
        val hmppsDomainEvent = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
        when (hmppsDomainEvent.additionalInformation.reason) {
          "RECALL" -> {
            releaseAndRecallService.prisonerRecalled(
              hmppsDomainEvent.additionalInformation.nomsNumber,
              hmppsDomainEvent.additionalInformation.prisonId,
              hmppsDomainEvent.occurredAt.toLocalDate()
            )
          }
        }
      }
      "prison-offender-events.prisoner.released" -> {
        val hmppsDomainEvent = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
        when (hmppsDomainEvent.additionalInformation.reason) {
          "RELEASED", "RELEASED_HOSPITAL" -> {
            releaseAndRecallService.prisonerReleased(
              hmppsDomainEvent.additionalInformation.nomsNumber,
              hmppsDomainEvent.occurredAt.toLocalDate()
            )
          }
        }
      }
      else -> log.info("Received a message wasn't expected $eventType")
    }
  }
}

data class AdditionalInformation(val nomsNumber: String, val reason: String, val prisonId: String)
data class HMPPSDomainEvent(val occurredAt: LocalDateTime, val additionalInformation: AdditionalInformation)

data class HMPPSEventType(val Value: String, val Type: String)
data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
data class HMPPSMessage(
  val Message: String,
  val MessageId: String,
  val MessageAttributes: HMPPSMessageAttributes
)
