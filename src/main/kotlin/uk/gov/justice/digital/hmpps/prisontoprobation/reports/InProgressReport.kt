package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import com.opencsv.bean.StatefulBeanToCsvBuilder
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class InProgressReport(private val messageRepository: MessageRepository) {
  @PreAuthorize("hasRole('ROLE_PTPU_REPORT')")
  fun generate(): String {
    return messageRepository.findAll().map {
      InProgress(
        bookingId = it.bookingId,
        createdDate = it.createdDate,
        deleteBy = LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC),
        eventType = it.eventType
      )
    }.asCSV()
  }
}

private fun <T> Iterable<T>.asCSV(): String {
  val list = this
  val csv = StringWriter().apply {
    StatefulBeanToCsvBuilder<T>(this).build().write(list.iterator())
  }
  return csv.toString()
}

data class InProgress(
  val bookingId: Long,
  val createdDate: LocalDateTime,
  val deleteBy: LocalDateTime,
  val eventType: String
)
