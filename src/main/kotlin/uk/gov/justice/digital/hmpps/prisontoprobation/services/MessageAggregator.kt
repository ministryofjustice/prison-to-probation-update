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
          is TryLater -> messageRepository.save(message.retry())
          is Done -> messageRepository.delete(message)
        }
      }
    }
  }

  private fun processMessage(message: Message): MessageResult {
    return try {
      val result = messageProcessor.processMessage(message.eventType, message.message)
      messageRepository.delete(message)
      result
    } catch (e: Exception) {
      log.error("Unable to process message ${message.eventType} for ${message.bookingId}", e)
      TryLater(message.bookingId)
    }
  }

  private fun aggregatedMessagesOrdered(messages: List<Message>): List<Pair<Message, Boolean>> {
    val allBookings = messages.map { it.bookingId }.distinct()
    val allMessagesForAllBookings = allBookings.flatMap { messageRepository.findByBookingId(it) }

    val groupedByBooking = allMessagesForAllBookings.groupBy { it.bookingId }
    val groupedFilteredAndSorted = groupedByBooking
        .map { (bookingId, messages) -> bookingId to messages.filterDuplicates() }
    val allMessagesAggregatedInOrder = groupedFilteredAndSorted.flatMap { it.second }
    return allMessagesForAllBookings.map { it to !allMessagesAggregatedInOrder.contains(it) }
  }

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

private fun List<Message>.filterDuplicates() = this.sortedWith(compareByPriorityDateDescending()).distinctBy { it.eventType }
private fun compareByPriorityDateDescending() = compareBy<Message> { it.toPriority }.thenByDescending { it.createdDate }

