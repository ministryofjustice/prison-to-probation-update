package uk.gov.justice.digital.hmpps.prisontoprobation.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisontoprobation.services.QueueAdminService

@RestController
@RequestMapping("/queue-admin", produces = [MediaType.APPLICATION_JSON_VALUE])
class QueueResource(
  private val queueAdminService: QueueAdminService
) {
  @PutMapping("/transfer-event-dlq")
  @PreAuthorize("hasRole('ROLE_PTPU_QUEUE_ADMIN')")
  @Operation(
    summary = "Transfers all DLQ messages to the main queue",
    description = "Requires ROLE_PTPU_QUEUE_ADMIN role"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role ROLE_PTPU_QUEUE_ADMIN")
    ]
  )
  fun transferEventDlq(): Unit = queueAdminService.transferEventMessages()

  @PutMapping("/purge-event-dlq")
  @PreAuthorize("hasRole('ROLE_PTPU_QUEUE_ADMIN')")
  @Operation(
    summary = "Purges the event dead letter queue",
    description = "Requires ROLE_PTPU_QUEUE_ADMIN role"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role ROLE_PTPU_QUEUE_ADMIN")
    ]
  )
  fun purgeEventDlq(): Unit = queueAdminService.clearAllDlqMessagesForEvent()

  @PutMapping("/queue-housekeeping")
  @Operation(
    summary = "Triggers maintenance of the queues",
    description = "This is an internal service which isn't exposed to the outside world. It is called from a Kubernetes CronJob named `queue-housekeeping-cronjob`"
  )
  fun eventQueueHousekeeping() {
    queueAdminService.transferEventMessages()
  }
}
