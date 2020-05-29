package uk.gov.justice.digital.hmpps.prisontoprobation.repositories

import org.socialsignin.spring.data.dynamodb.repository.EnableScan
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.repository.CrudRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDateTime


@EnableScan
interface MessageRepository: CrudRepository<Message, String> {
  fun findByBookingIdAndEventType(bookingId: Long, eventType: String) : List<Message>
  fun findByEventTypeAndRetryCount(eventType: String, retryCount: Int) : List<Message>
  fun findByRetryCountBetween(low: Int, high: Int) : List<Message>
  fun findByRetryCountAndCreatedDateBefore(retryCount: Int, createdDate: LocalDateTime)  : List<Message>
  fun findByBookingId(bookingId: Long) : List<Message>
}