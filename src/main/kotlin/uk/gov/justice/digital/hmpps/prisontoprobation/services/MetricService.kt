package uk.gov.justice.digital.hmpps.prisontoprobation.services

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

internal const val MOVEMENT_METRIC = "movement"
internal const val SENTENCE_DATES_METRIC = "sentenceDateChange"
internal const val STATUS_CHANGE_METRIC = "statusChange"
internal const val TOTAL_TYPE = "total"
internal const val FAIL_TYPE = "fail"
internal const val SUCCESS_TYPE = "success"

@Component
class MeterFactory {
  fun registerCounter(meterRegistry: MeterRegistry, name: String, description: String, type: String): Counter {
    val builder = Counter.builder(name).description(description)
    builder.tag("type", type)
    return builder.register(meterRegistry)
  }
}

@Service
class MetricService(meterRegistry: MeterRegistry, meterFactory: MeterFactory) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val movementReceivedCounter = meterFactory.registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of movements received", TOTAL_TYPE)
  private val movementsFailedCounter = meterFactory.registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of failed movements", FAIL_TYPE)
  private val movementsSuccessCounter = meterFactory.registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of successful movements", SUCCESS_TYPE)

  private val sentenceDatesTotalCounter = meterFactory.registerCounter(meterRegistry, SENTENCE_DATES_METRIC, "The number of sentence date updates received", TOTAL_TYPE)
  private val sentenceDatesFailedCounter = meterFactory.registerCounter(meterRegistry, SENTENCE_DATES_METRIC, "The number of failed sentence date updates", FAIL_TYPE)
  private val sentenceDatesSuccessCounter = meterFactory.registerCounter(meterRegistry, SENTENCE_DATES_METRIC, "The number of successful sentence date updates ", SUCCESS_TYPE)

  private val statusChangesTotalCounter = meterFactory.registerCounter(meterRegistry, STATUS_CHANGE_METRIC, "The number of status change updates received", TOTAL_TYPE)
  private val statusChangesFailedCounter = meterFactory.registerCounter(meterRegistry, STATUS_CHANGE_METRIC, "The number of failed status change updates ", FAIL_TYPE)
  private val statusChangesSuccessCounter = meterFactory.registerCounter(meterRegistry, STATUS_CHANGE_METRIC, "The number of successful status change updates ", SUCCESS_TYPE)

  fun retryEventFail(eventType: String) {
    when (eventType) {
      "IMPRISONMENT_STATUS-CHANGED" -> {
        statusChangesTotalCounter.increment()
        statusChangesFailedCounter.increment()
      }
      "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED" -> {
        sentenceDatesTotalCounter.increment()
        sentenceDatesFailedCounter.increment()
      }
      else -> log.error("Not counting metrics for failed message $eventType - not expected to retry")
    }
  }

  fun retryEventSuccess(eventType: String) {
    when (eventType) {
      "IMPRISONMENT_STATUS-CHANGED" -> {
        statusChangesTotalCounter.increment()
        statusChangesSuccessCounter.increment()
      }
      "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED" -> {
        sentenceDatesTotalCounter.increment()
        sentenceDatesSuccessCounter.increment()
      }
      else -> log.error("Not counting metrics for successful message $eventType - not expected to retry")
    }
  }

  fun movementReceived() = movementReceivedCounter.increment()
  fun movementFailed() = movementsFailedCounter.increment()
  fun movementSucceeded() = movementsSuccessCounter.increment()
}
