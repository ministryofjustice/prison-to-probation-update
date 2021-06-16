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
class PrisonerChangesListenerPusher(
  private val messageProcessor: MessageProcessor,
  private val retryService: MessageRetryService

) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  @JmsListener(destination = "#{@'hmpps.sqs-uk.gov.justice.digital.hmpps.prisontoprobation.config.SqsConfigProperties'.dpsQueue.queueName}")
  fun pushPrisonUpdateToProbation(requestJson: String?) {
    log.debug(requestJson)
    val (message, messageId, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $messageId type $eventType")
    log.debug("Will hand over to messageProcessor $messageProcessor")

    when (val result: MessageResult = messageProcessor.validateMessage(eventType, message)) {
      is TryLater -> retryService.scheduleForProcessing(result.bookingId, eventType, message, result.status)
      is Done -> result.message?.let { log.info("Ignoring message due to $it") }
    }
  }
}

data class EventType(val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
