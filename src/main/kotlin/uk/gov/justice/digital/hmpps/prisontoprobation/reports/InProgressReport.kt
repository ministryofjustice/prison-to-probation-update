package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import com.opencsv.bean.StatefulBeanToCsvBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class InProgressReport(private val messageRepository: MessageRepository) {
  @PreAuthorize("hasRole('ROLE_PTPU_REPORT')")
  @Operation(
    summary = "InProgress report",
    description = "A report of all matches currently in progress",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "CSV report",
        content = arrayOf(
          Content(
            mediaType = "text/csv",
            examples = arrayOf(
              ExampleObject(
                """
                "BOOKINGID","CREATEDDATE","DELETEBY","EVENTTYPE"
                "2672916","2020-12-10T10:38:26.226873","2020-12-18T10:38:26","IMPRISONMENT_STATUS-CHANGED"
                """
              )
            )
          )
        )
      )
    ]
  )
  fun generate(): String {
    return messageRepository.findAllByProcessedDateIsNull().map {
      InProgress(
        bookingId = it.bookingId,
        createdDate = it.createdDate,
        deleteBy = LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC),
        eventType = it.eventType,
        offenderNo = it.offenderNo,
        bookingNo = it.bookingNo,
        crns = it.matchingCrns,
        locationId = it.locationId,
        location = it.locationDescription,
        legalStatus = it.legalStatus,
        recall = it.recall,
        status = it.status
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
  val eventType: String,
  val offenderNo: String?,
  val bookingNo: String?,
  val crns: String?,
  val locationId: String?,
  val location: String?,
  val legalStatus: String?,
  val recall: Boolean?,
  val status: String?,
)
