package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.NO_MATCH
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.NO_MATCH_WITH_SENTENCE_DATE
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.TOO_MANY_MATCHES
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class NotMatchedReport(private val messageRepository: MessageRepository) {
  @PreAuthorize("hasRole('ROLE_PTPU_REPORT')")
  @Operation(
    summary = "NotMatched report",
    description = "A report of all records not matched yet",
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
                "BOOKINGID","BOOKINGNO","CREATEDDATE","CRNS","DELETEBY","EVENTTYPE","LEGALSTATUS","LOCATION","LOCATIONID","OFFENDERNO","RECALL","STATUS"
                "2672916","12345V","2020-12-09T15:15:50","","2020-12-19T15:15:50","IMPRISONMENT_STATUS-CHANGED","SENTENCED","Moorland HMP","MDI","A1234GY","false","NOT_MATCHED"
                """
              )
            )
          )
        )
      )
    ]
  )
  fun generate(daysOld: Long): String {
    return messageRepository.findAllByStatusInAndCreatedDateLessThan(
      listOf(
        NO_MATCH.name,
        NO_MATCH_WITH_SENTENCE_DATE.name,
        TOO_MANY_MATCHES.name
      ),
      LocalDateTime.now().minusDays(daysOld)
    ).map {
      NotMatched(
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

data class NotMatched(
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
