package uk.gov.justice.digital.hmpps.prisontoprobation.services

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal const val MOVEMENT_METRIC = "ptpu.movement"
internal const val SENTENCE_DATES_METRIC = "ptpu.sentenceDateChange"
internal const val STATUS_CHANGE_METRIC = "ptpu.statusChange"
internal const val TOTAL_TYPE = "total"
internal const val FAIL_TYPE = "fail"
internal const val SUCCESS_TYPE = "success"
internal const val SUCCESS_AFTER_RETRIES_TYPE = "successAfterRetries"
internal const val SUCCESS_AFTER_TIME_TYPE = "successAfterTimeDays"
internal const val LAST_RETRY_WINDOW_HOURS = 24L
internal const val RETRIES_EXPECTED_MAX = 50.0
internal const val AGE_EXPECTED_MAX_DAYS = 20L

@Component
class MeterFactory {
  fun registerCounter(meterRegistry: MeterRegistry, name: String, description: String, type: String): Counter =
    Counter.builder(name)
      .description(description)
      .tag("type", type)
      .register(meterRegistry)

  fun registerRetryDistribution(meterRegistry: MeterRegistry, name: String, description: String, type: String): DistributionSummary =
    DistributionSummary.builder(name)
      .publishPercentileHistogram()
      .minimumExpectedValue(0.1)
      .maximumExpectedValue(RETRIES_EXPECTED_MAX)
      .baseUnit("retries")
      .description(description)
      .tag("eventType", type)
      .register(meterRegistry)

  fun registerMessageAgeTimer(meterRegistry: MeterRegistry, name: String, description: String, type: String): Timer =
    Timer.builder(name)
      .publishPercentileHistogram()
      .minimumExpectedValue(Duration.ofDays(1))
      .maximumExpectedValue(Duration.ofDays(AGE_EXPECTED_MAX_DAYS))
      .description(description)
      .tag("eventType", type)
      .register(meterRegistry)
}

/**
 * Unretryable events must handle their own metrics in the service that processes the events
 */
@Service
class UnretryableEventMetricsService(meterRegistry: MeterRegistry, meterFactory: MeterFactory) {

  private val movementReceivedCounter =
    meterFactory.registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of movements received", TOTAL_TYPE)
  private val movementsFailedCounter =
    meterFactory.registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of failed movements", FAIL_TYPE)
  private val movementsSuccessCounter =
    meterFactory.registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of successful movements", SUCCESS_TYPE)

  fun movementReceived() = movementReceivedCounter.increment()
  fun movementFailed() = movementsFailedCounter.increment()
  fun movementSucceeded() = movementsSuccessCounter.increment()
}

/**
 * Retryable events' metrics are handled by the MessageProcessor which delegates to this class.
 * It is this class's responsibility to:
 *  1. Ignore unretryable events
 *  2. Decide when a retryable event will not be retried again
 */
@Service
class RetryableEventMetricsService(meterRegistry: MeterRegistry, meterFactory: MeterFactory) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val sentenceDatesTotalCounter = meterFactory.registerCounter(
    meterRegistry,
    SENTENCE_DATES_METRIC,
    "The number of sentence date updates received",
    TOTAL_TYPE
  )
  private val sentenceDatesFailedCounter = meterFactory.registerCounter(
    meterRegistry,
    SENTENCE_DATES_METRIC,
    "The number of failed sentence date updates",
    FAIL_TYPE
  )
  private val sentenceDatesSuccessCounter = meterFactory.registerCounter(
    meterRegistry,
    SENTENCE_DATES_METRIC,
    "The number of successful sentence date updates ",
    SUCCESS_TYPE
  )
  private val sentenceDatesRetriesDistribution = meterFactory.registerRetryDistribution(
    meterRegistry,
    SENTENCE_DATES_METRIC,
    "The number of retries before a successful update",
    SUCCESS_AFTER_RETRIES_TYPE
  )
  private val sentenceDatesSuccessTimer = meterFactory.registerMessageAgeTimer(
    meterRegistry,
    SENTENCE_DATES_METRIC,
    "The time in days before a successful update",
    SUCCESS_AFTER_TIME_TYPE
  )

  private val statusChangesTotalCounter = meterFactory.registerCounter(
    meterRegistry,
    STATUS_CHANGE_METRIC,
    "The number of status change updates received",
    TOTAL_TYPE
  )
  private val statusChangesFailedCounter = meterFactory.registerCounter(
    meterRegistry,
    STATUS_CHANGE_METRIC,
    "The number of failed status change updates ",
    FAIL_TYPE
  )
  private val statusChangesSuccessCounter = meterFactory.registerCounter(
    meterRegistry,
    STATUS_CHANGE_METRIC,
    "The number of successful status change updates ",
    SUCCESS_TYPE
  )
  private val statusChangeRetriesDistribution = meterFactory.registerRetryDistribution(
    meterRegistry,
    STATUS_CHANGE_METRIC,
    "The number of retries before a successful update",
    SUCCESS_AFTER_RETRIES_TYPE
  )
  private val statusChangeSuccessTimer = meterFactory.registerMessageAgeTimer(
    meterRegistry,
    STATUS_CHANGE_METRIC,
    "The time in days before a successful update",
    SUCCESS_AFTER_TIME_TYPE
  )

  fun eventFailed(eventType: String, deleteBy: LocalDateTime) =
    takeIf { readyForDelete(deleteBy) }
      ?.also {
        when (eventType) {
          "IMPRISONMENT_STATUS-CHANGED" -> {
            statusChangesTotalCounter.increment()
            statusChangesFailedCounter.increment()
          }
          "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED" -> {
            sentenceDatesTotalCounter.increment()
            sentenceDatesFailedCounter.increment()
          }
        }
      }

  private fun readyForDelete(deleteBy: LocalDateTime) = deleteBy.minus(LAST_RETRY_WINDOW_HOURS, ChronoUnit.HOURS) < LocalDateTime.now()

  fun eventSucceeded(eventType: String, createdDate: LocalDateTime, retries: Int = 0) =
    Duration.ofDays(createdDate.until(LocalDateTime.now(), ChronoUnit.DAYS) + 1)!! // +1 because a partial day counts as a whole day
      .also { duration ->
        when (eventType) {
          "IMPRISONMENT_STATUS-CHANGED" -> {
            statusChangesTotalCounter.increment()
            statusChangesSuccessCounter.increment()
            statusChangeRetriesDistribution.record(retries.toDouble())
            statusChangeSuccessTimer.record(duration)
          }
          "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED" -> {
            sentenceDatesTotalCounter.increment()
            sentenceDatesSuccessCounter.increment()
            sentenceDatesRetriesDistribution.record(retries.toDouble())
            sentenceDatesSuccessTimer.record(duration)
          }
        }
      }
}
