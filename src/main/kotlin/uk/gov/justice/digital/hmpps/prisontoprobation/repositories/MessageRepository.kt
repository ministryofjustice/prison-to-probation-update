package uk.gov.justice.digital.hmpps.prisontoprobation.repositories

import org.socialsignin.spring.data.dynamodb.repository.EnableScan
import org.springframework.data.repository.CrudRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message


@EnableScan
interface MessageRepository: CrudRepository<Message, String> {
  fun findByBookingIdAndEventType(bookingId: Long, eventType: String) : List<Message>
  fun findByEventTypeAndRetryCount(eventType: String, retryCount: Int) : List<Message>
  fun findByRetryCountBetween(low: Int, high: Int) : List<Message>

}