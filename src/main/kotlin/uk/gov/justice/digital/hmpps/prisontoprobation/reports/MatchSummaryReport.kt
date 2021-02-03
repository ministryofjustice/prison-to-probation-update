package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseState.NO_LONGER_VALID
import java.time.LocalDateTime

@Service
class MatchSummaryReport(private val messageRepository: MessageRepository) {
  @PreAuthorize("hasRole('ROLE_PTPU_REPORT')")
  @Operation(
    summary = "Match summary report",
    description = "A report showing current summary of matching",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "JSON report",
      )
    ]
  )
  fun generate(
    locationId: String?,
    createdDateStartDateTime: LocalDateTime?,
    createdDateEndDateTime: LocalDateTime?,
    slaDays: Long,
  ): MatchSummary {
    operator fun MatchSummary.plus(message: Message): MatchSummary {
      return this.copy(
        total = this.total + 1,
        completed = this.completed.addWhenProcessed(message),
        waiting = this.waiting.addWhenWaiting(message, slaDays),
        `exceeded-sla` = this.`exceeded-sla`.addWhenExceededSLA(message, slaDays),
      )
    }

    return messageRepository.findAll()
      .asSequence()
      .filter { record -> locationId?.let { record.locationId == locationId } ?: true }
      .filter { record -> createdDateStartDateTime?.let { record.createdDate.isAfter(createdDateStartDateTime) } ?: true }
      .filter { record -> createdDateEndDateTime?.let { record.createdDate.isBefore(createdDateEndDateTime) } ?: true }
      .fold(MatchSummary(), { summary, message -> summary + message })
  }
}

data class MatchSummary(
  val total: Long = 0,
  val completed: Completed = Completed(),
  @Schema(title = "waiting", description = "waiting to be processed")
  val waiting: Waiting = Waiting(),
  @Schema(title = "exceeded-sla", description = "exceeded SLA but may still be processed in the future")
  val `exceeded-sla`: ExceededSLA = ExceededSLA()
)

data class Completed(
  val total: Long = 0,
  val success: Long = 0,
  val rejected: Long = 0,
)

data class Waiting(
  val total: Long = 0,
  val new: Long = 0,
  val retry: Long = 0,
  val category: Category = Category(),
)

data class ExceededSLA(
  val total: Long = 0,
  val category: Category = Category(),
)

data class Category(
  val `no-match`: Long = 0,
  val `no-match-sentence`: Long = 0,
  val `too-many-matches`: Long = 0,
  val `book-number-set-fail`: Long = 0,
  val `location-set-fail`: Long = 0,
  val `key-dates-set-fail`: Long = 0,
  val `error-fail`: Long = 0,
)

operator fun Completed.plus(message: Message): Completed =
  this.copy(
    total = this.total + 1,
    rejected = this.rejected.incrementWhenTrue(message.isRejected()),
    success = this.success.incrementWhenTrue(message.isRejected().not()),
  )

operator fun Waiting.plus(message: Message): Waiting =
  this.copy(
    total = this.total + 1,
    new = this.new.incrementWhenTrue(message.isNew()),
    retry = this.retry.incrementWhenTrue(message.isNew().not()),
    category = this.category + message,
  )

operator fun ExceededSLA.plus(message: Message): ExceededSLA =
  this.copy(
    total = this.total + 1,
    category = this.category + message,
  )

operator fun Category.plus(message: Message): Category =
  when (message.status) {
    SynchroniseState.NO_MATCH.name -> this.copy(`no-match` = this.`no-match` + 1)
    SynchroniseState.NO_MATCH_WITH_SENTENCE_DATE.name -> this.copy(`no-match-sentence` = this.`no-match-sentence` + 1)
    SynchroniseState.TOO_MANY_MATCHES.name -> this.copy(`too-many-matches` = this.`too-many-matches` + 1)
    SynchroniseState.BOOKING_NUMBER_NOT_ASSIGNED.name -> this.copy(`book-number-set-fail` = this.`book-number-set-fail` + 1)
    SynchroniseState.LOCATION_NOT_UPDATED.name -> this.copy(`location-set-fail` = this.`location-set-fail` + 1)
    SynchroniseState.KEY_DATES_NOT_UPDATED.name -> this.copy(`key-dates-set-fail` = this.`key-dates-set-fail` + 1)
    SynchroniseState.ERROR.name -> this.copy(`error-fail` = this.`error-fail` + 1)
    else -> this
  }

private fun Message.isRejected() = this.status == NO_LONGER_VALID.name
private fun Message.isNew() = this.retryCount == 0
private fun Message.isProcessed() = this.processedDate != null
private fun Message.hasExceededSLA(sla: Long): Boolean = this.createdDate.isBefore(LocalDateTime.now().minusDays(sla)) && this.isProcessed().not()

private fun Message.isWaiting(sla: Long) = this.isProcessed().not() && hasExceededSLA(sla).not()

private fun Long.incrementWhenTrue(condition: Boolean): Long = if (condition) this + 1 else this
private fun Completed.addWhenProcessed(message: Message): Completed = if (message.isProcessed()) this + message else this
private fun Waiting.addWhenWaiting(message: Message, sla: Long): Waiting = if (message.isWaiting(sla)) this + message else this
private fun ExceededSLA.addWhenExceededSLA(message: Message, sla: Long): ExceededSLA = if (message.hasExceededSLA(sla)) this + message else this
