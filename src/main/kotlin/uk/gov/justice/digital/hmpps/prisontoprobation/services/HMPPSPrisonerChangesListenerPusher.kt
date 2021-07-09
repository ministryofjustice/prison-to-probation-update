package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Service
@Profile("!no-queue-listener")
class HMPPSPrisonerChangesListenerPusher(
  private val releaseAndRecallService: ReleaseAndRecallService,
  private val objectMapper: ObjectMapper
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "hmppseventqueue", containerFactory = "hmppsQueueContainerFactoryProxy")
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
              hmppsDomainEvent.occurredAtLocalDate()
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
              hmppsDomainEvent.additionalInformation.prisonId,
              hmppsDomainEvent.occurredAtLocalDate()
            )
          }
        }
      }
      else -> log.info("Received a message wasn't expected $eventType")
    }
  }
}

data class AdditionalInformation(val nomsNumber: String, val reason: String, val prisonId: String)
data class HMPPSDomainEvent(val occurredAt: String, val additionalInformation: AdditionalInformation) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun occurredAtLocalDate(): LocalDate {
    // to allow for breaking change to date format try to parse as offset iso date and iso date
    return try {
      OffsetDateTime.parse(occurredAt).toLocalDate()
    } catch (e: DateTimeParseException) {
      log.warn("Message contained old style local date $occurredAt")
      LocalDateTime.parse(occurredAt).toLocalDate()
    }
  }
}

data class HMPPSEventType(val Value: String, val Type: String)
data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
data class HMPPSMessage(
  val Message: String,
  val MessageId: String,
  val MessageAttributes: HMPPSMessageAttributes
)
