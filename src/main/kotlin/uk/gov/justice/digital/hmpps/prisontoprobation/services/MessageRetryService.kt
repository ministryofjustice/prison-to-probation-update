package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message


@Service
class MessageRetryService(private val messageRepository: MessageRepository) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }


  fun retryLater(bookingId: Long, eventType: String, message: String) {
    log.info("Registering a retry for booking $bookingId for event $eventType")
    // TODO first stab, will need to find and update existing row etc
    messageRepository.save(Message(bookingId = bookingId, eventType = eventType, message = message))
  }

}