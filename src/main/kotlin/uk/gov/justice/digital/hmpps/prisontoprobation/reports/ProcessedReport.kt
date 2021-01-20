package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class ProcessedReport(private val messageRepository: MessageRepository) {
  @PreAuthorize("hasRole('ROLE_PTPU_REPORT')")
  @Operation(
    summary = "Processed report",
    description = "A report of offenders that have been successful processed",
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
                "BOOKINGID","BOOKINGNO","CREATEDDATE","CRNS","DELETEBY","EVENTTYPE","LEGALSTATUS","LOCATION","LOCATIONID","OFFENDERNO","PROCESSEDDATE","RECALL","STATUS"
                "2672916","12345V","2020-12-09T15:15:50","X12345,X87654","2020-12-19T15:15:50","IMPRISONMENT_STATUS-CHANGED","SENTENCED","Moorland HMP","MDI","A1234GY","2020-12-09T15:15:50","false","BOOKING_NUMBER_NOT_ASSIGNED"
                """
              )
            )
          )
        )
      )
    ]
  )
  fun generate(
    locationId: String?,
    eventType: String?,
    processedDateStartDateTime: LocalDateTime?,
    processedDateEndDateTime: LocalDateTime?,
    createdDateStartDateTime: LocalDateTime?,
    createdDateEndDateTime: LocalDateTime?,
  ): String {
    return messageRepository.findAllByProcessedDateIsNotNull()
      .asSequence()
      .filter { record -> locationId?.let { record.locationId == locationId } ?: true }
      .filter { record -> eventType?.let { record.eventType == eventType } ?: true }
      .filter { record ->
        processedDateStartDateTime?.let { record.processedDate!!.isAfter(processedDateStartDateTime) } ?: true
      }
      .filter { record ->
        processedDateEndDateTime?.let { record.processedDate!!.isBefore(processedDateEndDateTime) } ?: true
      }
      .filter { record ->
        createdDateStartDateTime?.let { record.createdDate.isAfter(createdDateStartDateTime) } ?: true
      }
      .filter { record ->
        createdDateEndDateTime?.let { record.createdDate.isBefore(createdDateEndDateTime) } ?: true
      }
      .sortedBy { it.createdDate }
      .map {
        Processed(
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
          status = it.status,
          processedDate = it.processedDate!!
        )
      }
      .toList().asCSV()
  }
}

data class Processed(
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
  val processedDate: LocalDateTime,
)
