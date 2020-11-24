package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class MessageRetryService(
  private val messageRepository: MessageRepository,
  private val messageProcessor: MessageProcessor,
  private val metricService: MetricService,
  @Value("\${dynamodb.message.expiryHours}")
  private val expiryHours: Long
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun scheduleForProcessing(bookingId: Long, eventType: String, message: String) {
    log.info("Registering an initial processing for booking $bookingId for event $eventType")
    messageRepository.save(
      Message(
        bookingId = bookingId,
        eventType = eventType,
        message = message,
        retryCount = 0,
        deleteBy = LocalDateTime.now().plusHours(expiryHours).toEpochSecond(ZoneOffset.UTC)
      )
    )
  }

  fun retryLater(bookingId: Long, eventType: String, message: String) {
    log.info("Registering a retry for booking $bookingId for event $eventType")
    messageRepository.save(
      Message(
        bookingId = bookingId,
        eventType = eventType,
        message = message,
        deleteBy = LocalDateTime.now().plusHours(expiryHours).toEpochSecond(ZoneOffset.UTC)
      )
    )
  }

  fun retryShortTerm() {
    retryForAttemptsMade(1..4)
  }

  fun retryMediumTerm() {
    retryForAttemptsMade(5..10)
  }

  fun retryLongTerm() {
    retryForAttemptsMade(11..Int.MAX_VALUE)
  }

  private fun retryForAttemptsMade(range: IntRange) {
    val messages = messageRepository.findByRetryCountBetween(range.first, range.last)
    messages.forEach {
      log.info("Retrying ${it.eventType} for ${it.bookingId} after ${it.retryCount} attempts")
      when (val result: MessageResult = processMessage(it.eventType, it.message, it.bookingId)) {
        is TryLater -> {
          log.info("Still not successful ${it.eventType} for ${it.bookingId} after ${it.retryCount} attempts")
          messageRepository.save(it.retry(result.retryUntil))
          countFailIfLastAttempt(it.eventType, Instant.ofEpochSecond(it.deleteBy))
        }
        is Done -> {
          log.info("Success ${it.eventType} for ${it.bookingId} after ${it.retryCount} attempts")
          messageRepository.delete(it)
          result.message?.let { logMessage -> PrisonerChangesListenerPusher.log.info(logMessage) }
          countSuccess(it.eventType)
        }
      }
    }
  }

  private fun countFailIfLastAttempt(eventType: String, deleteBy: Instant) {
    if (deleteBy.minus(24, ChronoUnit.HOURS) < Instant.now()) {
      metricService.retryEventFail(eventType)
    }
  }

  private fun countSuccess(eventType: String) {
    metricService.retryEventSuccess(eventType)
  }

  private fun processMessage(eventType: String, message: String, bookingId: Long): MessageResult {
    return try {
      messageProcessor.processMessage(eventType, message)
    } catch (e: Exception) {
      log.error("Exception while processing $eventType for $bookingId", e)
      TryLater(bookingId)
    }
  }
}
