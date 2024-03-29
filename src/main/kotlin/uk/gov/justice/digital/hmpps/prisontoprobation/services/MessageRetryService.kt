package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository

@Service
class MessageRetryService(
  private val messageRepository: MessageRepository,
  private val messageProcessor: MessageProcessor,
  private val offenderService: OffenderService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun scheduleForProcessing(bookingId: Long, eventType: String, message: String, status: SynchroniseStatus) {
    val booking = offenderService.getBooking(bookingId)
    messageRepository.save(
      Message(
        bookingId = bookingId,
        eventType = eventType,
        message = message,
        retryCount = 0,
        reportable = true,
        offenderNo = booking.offenderNo,
        bookingNo = booking.bookingNo,
        locationId = booking.agencyId,
        locationDescription = booking.locationDescription,
        recall = booking.recall,
        legalStatus = booking.legalStatus,
        status = status.state.name,
      ),
    )
  }

  fun retryShortTerm() = retryForAttemptsMade(1..4)

  fun retryMediumTerm() = retryForAttemptsMade(5..10)

  fun retryLongTerm() = retryForAttemptsMade(11..Int.MAX_VALUE)

  private fun retryForAttemptsMade(range: IntRange) =
    messageRepository.findByRetryCountBetweenAndProcessedDateIsNull(range.first, range.last).forEach {
      log.debug("Retrying ${it.eventType} for ${it.bookingId} after ${it.retryCount} attempts")
      when (val result: MessageResult = processMessage(it)) {
        is TryLater -> {
          log.debug("Still not successful ${it.eventType} for ${it.bookingId} after ${it.retryCount} attempts")
          messageRepository.save(it.retry(result.retryUntil, result.status))
        }
        is Done -> {
          log.debug("Success ${it.eventType} for ${it.bookingId} after ${it.retryCount} attempts")
          messageRepository.save(it.markAsProcessed(result.status))
          result.message?.let { logMessage -> PrisonerChangesListenerPusher.log.debug(logMessage) }
        }
      }
    }

  private fun processMessage(message: Message): MessageResult =
    try {
      messageProcessor.processMessage(message)
    } catch (e: Exception) {
      log.error("Exception while processing ${message.eventType} for ${message.bookingId}", e)
      TryLater(message.bookingId, status = SynchroniseStatus(state = SynchroniseState.ERROR))
    }
}
