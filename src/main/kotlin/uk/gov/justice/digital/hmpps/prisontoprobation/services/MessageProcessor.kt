package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class MessageProcessor(
  private val bookingChangeService: BookingChangeService,
  private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService,
  private val retryableEventMetricsService: RetryableEventMetricsService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  fun processMessage(message: Message): MessageResult =
    callMessageHandler(message.eventType, message.message)
      .also {
        when (it) {
          is Done -> retryableEventMetricsService.eventSucceeded(
            message.eventType,
            message.createdDate,
            message.retryCount,
          )
          is TryLater -> retryableEventMetricsService.eventFailed(
            message.eventType,
            LocalDateTime.ofEpochSecond(message.deleteBy, 0, OffsetDateTime.now().offset),
          )
        }
      }

  private fun callMessageHandler(eventType: String, message: String): MessageResult =
    when (eventType) {
      "IMPRISONMENT_STATUS-CHANGED" -> imprisonmentStatusChangeService.processImprisonmentStatusChangeAndUpdateProbation(
        fromJson(message),
      )
      "BOOKING_NUMBER-CHANGED" -> bookingChangeService.processBookingNumberChangedAndUpdateProbation(fromJson(message))
      else -> {
        Done("We received a message of event type $eventType which I really wasn't expecting")
      }
    }

  fun validateMessage(eventType: String, message: String): MessageResult =
    when (eventType) {
      "IMPRISONMENT_STATUS-CHANGED" -> imprisonmentStatusChangeService.validateImprisonmentStatusChange(fromJson(message))
      "BOOKING_NUMBER-CHANGED" -> bookingChangeService.validateBookingNumberChange(fromJson(message))
      else -> {
        Done("We received a message of event type $eventType which I really wasn't expecting")
      }
    }

  private inline fun <reified T> fromJson(message: String): T = gson.fromJson(message, T::class.java)
}

data class ExternalPrisonerMovementMessage(val bookingId: Long, val movementSeq: Long)
data class BookingNumberChangedMessage(val bookingId: Long)
data class ImprisonmentStatusChangesMessage(val bookingId: Long, val imprisonmentStatusSeq: Long)

enum class SynchroniseState {
  ERROR,
  NOT_VALID,
  VALIDATED,
  NO_MATCH,
  NO_MATCH_WITH_SENTENCE_DATE,
  TOO_MANY_MATCHES,
  BOOKING_NUMBER_NOT_ASSIGNED,
  LOCATION_NOT_UPDATED,
  COMPLETED,
  NO_LONGER_VALID,
}

data class SynchroniseStatus(val matchingCrns: String? = null, val state: SynchroniseState = SynchroniseState.VALIDATED)
sealed class MessageResult
class TryLater(
  val bookingId: Long,
  val retryUntil: LocalDate? = null,
  val status: SynchroniseStatus = SynchroniseStatus(state = SynchroniseState.VALIDATED),
) : MessageResult()

class Done(
  val message: String? = null,
  val status: SynchroniseStatus = SynchroniseStatus(state = SynchroniseState.COMPLETED),
) : MessageResult()
