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
  private val holdBackDuration: Duration,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processMessagesForNextBookingSets() {
    val messages = messageRepository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(
      0,
      LocalDateTime.now().minus(holdBackDuration),
    )

    val (messagesToProcess, messagesToDiscard) = aggregatedMessagesOrdered(messages)

    messageRepository.deleteAll(messagesToDiscard)

    messagesToProcess.forEach {
      when (val result = processMessage(it)) {
        is TryLater -> messageRepository.save(it.retry(status = result.status))
        is Done -> messageRepository.save(it.markAsProcessed(status = result.status))
      }
    }
  }

  private fun processMessage(message: Message): MessageResult {
    return try {
      messageProcessor.processMessage(message)
    } catch (e: Exception) {
      log.error("Unable to process message ${message.eventType} for ${message.bookingId}", e)
      TryLater(message.bookingId, status = SynchroniseStatus(state = SynchroniseState.ERROR))
    }
  }

  private fun aggregatedMessagesOrdered(messages: List<Message>): Pair<List<Message>, List<Message>> {
    val allBookings = messages.map { it.bookingId }.distinct()
    // now get all other messages regardless of age or number or retires that we can process as a batch
    val allMessagesForAllBookings = allBookings.flatMap { messageRepository.findByBookingIdAndProcessedDateIsNull(it) }

    val groupedByBooking = allMessagesForAllBookings.groupBy { it.bookingId }

    // for each booking only process one (the latest ) of each time and order by type
    val groupedDeduplicatedAndOrdered = groupedByBooking
      .map { (bookingId, messages) -> bookingId to messages.filterDuplicatesAndOrder() }
    val allMessagesAggregatedInOrder = groupedDeduplicatedAndOrdered.flatMap { it.second }

    // return list of messages to process along with duplicates that can be thrown away
    return Pair(allMessagesAggregatedInOrder, allMessagesForAllBookings - allMessagesAggregatedInOrder)
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

private fun List<Message>.filterDuplicatesAndOrder() = this.sortedWith(compareByPriorityDateDescending()).distinctBy { it.eventType }
private fun compareByPriorityDateDescending() = compareBy<Message> { it.toPriority }.thenByDescending { it.createdDate }
