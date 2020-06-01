package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.time.Duration
import java.time.LocalDateTime


@Service
class MessageAggregator(
    private val messageRepository: MessageRepository,
    private val messageProcessor: MessageProcessor,
    @Value("\${prisontoprobation.hold-back.duration}")
    private val holdBackDuration: Duration
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processMessagesForNextBookingSets() {
    val messages = messageRepository.findByRetryCountAndCreatedDateBefore(0, LocalDateTime.now().minus(holdBackDuration))
    log.debug("${messages.size} candidate messages for initial processing")

    aggregatedMessagesOrdered(messages).forEach {
      val (message, discard) = it
      if (discard) {
        messageRepository.delete(message)
      } else {
        when (processMessage(message)) {
          is RetryLater -> messageRepository.save(message.retry())
          is Done -> messageRepository.delete(message)
        }
      }
    }
  }

  private fun allAggregatedMessagesForBooking(message: Message): List<Pair<Message, Boolean>> {
    val allMessages = messageRepository.findByBookingId(message.bookingId).sortedBy {it.toPriority }
    val uniqueMessages = allMessages.distinctBy { it.eventType }
    return allMessages.map { it to !uniqueMessages.contains(it) }
  }

  private fun processMessage(message: Message): MessageResult {
    return try {
      val result = messageProcessor.processMessage(message.eventType, message.message)
      messageRepository.delete(message)
      result
    } catch (e: Exception) {
      log.error("Unable to process message ${message.eventType} for ${message.bookingId}", e)
      RetryLater(message.bookingId)
    }
  }

  private fun aggregatedMessagesOrdered(messages: List<Message>): List<Pair<Message, Boolean>> =
      messages.sortedBy { it.createdDate }.flatMap { allAggregatedMessagesForBooking(it) }
}

val Message.toPriority: Int
  get() {
    return when (this.eventType) {
      "IMPRISONMENT_STATUS-CHANGED" -> 0
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> 1
      "SENTENCE_DATES-CHANGED" -> 2
      "CONFIRMED_RELEASE_DATE-CHANGED" -> 3
      "BOOKING_NUMBER-CHANGED" -> 4
      else -> 99
    }
  }

