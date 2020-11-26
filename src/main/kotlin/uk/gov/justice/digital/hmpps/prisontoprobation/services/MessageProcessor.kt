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
  private val prisonMovementService: PrisonMovementService,
  private val bookingChangeService: BookingChangeService,
  private val imprisonmentStatusChangeService: ImprisonmentStatusChangeService,
  private val sentenceDatesChangeService: SentenceDatesChangeService,
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
          is Done -> retryableEventMetricsService.eventSucceeded(message.eventType, message.createdDate, message.retryCount)
          is TryLater -> retryableEventMetricsService.eventFailed(message.eventType, LocalDateTime.ofEpochSecond(message.deleteBy, 0, OffsetDateTime.now().offset))
        }
      }

  private fun callMessageHandler(eventType: String, message: String): MessageResult =
    when (eventType) {
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> prisonMovementService.processMovementAndUpdateProbation(fromJson(message))
      "IMPRISONMENT_STATUS-CHANGED" -> imprisonmentStatusChangeService.processImprisonmentStatusChangeAndUpdateProbation(fromJson(message))
      "BOOKING_NUMBER-CHANGED" -> bookingChangeService.processBookingNumberChangedAndUpdateProbation(fromJson(message))
      "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED" -> sentenceDatesChangeService.processSentenceDateChangeAndUpdateProbation(fromJson(message))
      else -> {
        Done("We received a message of event type $eventType which I really wasn't expecting")
      }
    }

  fun validateMessage(eventType: String, message: String): MessageResult =
    when (eventType) {
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> prisonMovementService.validateMovement(fromJson(message))
      "IMPRISONMENT_STATUS-CHANGED" -> imprisonmentStatusChangeService.validateImprisonmentStatusChange(fromJson(message))
      "BOOKING_NUMBER-CHANGED" -> bookingChangeService.validateBookingNumberChange(fromJson(message))
      "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED" -> sentenceDatesChangeService.validateSentenceDateChange(fromJson(message))
      else -> {
        Done("We received a message of event type $eventType which I really wasn't expecting")
      }
    }

  private inline fun <reified T> fromJson(message: String): T = gson.fromJson(message, T::class.java)
}

data class ExternalPrisonerMovementMessage(val bookingId: Long, val movementSeq: Long)
data class BookingNumberChangedMessage(val bookingId: Long)
data class ImprisonmentStatusChangesMessage(val bookingId: Long, val imprisonmentStatusSeq: Long)
data class SentenceKeyDateChangeMessage(val bookingId: Long)

sealed class MessageResult
class TryLater(val bookingId: Long, val retryUntil: LocalDate? = null) : MessageResult()
class Done(val message: String? = null) : MessageResult()
