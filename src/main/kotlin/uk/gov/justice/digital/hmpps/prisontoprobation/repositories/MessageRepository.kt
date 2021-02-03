package uk.gov.justice.digital.hmpps.prisontoprobation.repositories

import org.socialsignin.spring.data.dynamodb.repository.EnableScan
import org.springframework.data.repository.CrudRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDateTime

@EnableScan
interface MessageRepository : CrudRepository<Message, String> {
  fun findByBookingIdAndEventType(bookingId: Long, eventType: String): List<Message>
  fun findByEventType(eventType: String): List<Message>
  fun findByEventTypeAndRetryCount(eventType: String, retryCount: Int): List<Message>
  fun findByRetryCountBetweenAndProcessedDateIsNull(low: Int, high: Int): List<Message>
  fun findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(
    retryCount: Int,
    createdDate: LocalDateTime
  ): List<Message>

  fun findByBookingId(bookingId: Long): List<Message>
  fun findByBookingIdAndProcessedDateIsNull(bookingId: Long): List<Message>
  fun findAllByProcessedDateIsNull(): List<Message>
  fun findAllByProcessedDateIsNotNull(): List<Message>
  fun findAllByStatusInAndCreatedDateLessThan(status: List<String>, plusDays: LocalDateTime): List<Message>
}
