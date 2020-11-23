package uk.gov.justice.digital.hmpps.prisontoprobation.services

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

private const val MOVEMENT_METRIC = "movement"
private const val SENTENCE_DATES_METRIC = "sentenceDateChange"
private const val STATUS_CHANGE_METRIC = "statusChange"
private const val TOTAL_TYPE = "total"
private const val FAIL_TYPE = "fail"
private const val SUCCESS_TYPE = "success"

@Service
class MetricService(meterRegistry: MeterRegistry) {

  private val movementReceivedCounter = registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of movements received", TOTAL_TYPE)
  private val movementsFailedCounter = registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of failed movements", FAIL_TYPE)
  private val movementsSuccessCounter = registerCounter(meterRegistry, MOVEMENT_METRIC, "The number of successful movements", SUCCESS_TYPE)
  private val sentenceDatesTotalCounter = registerCounter(meterRegistry, SENTENCE_DATES_METRIC, "The number of sentence date updates received", TOTAL_TYPE)
  private val sentenceDatesFailedCounter = registerCounter(meterRegistry, SENTENCE_DATES_METRIC, "The number of failed sentence date updates", FAIL_TYPE)
  private val sentenceDatesSuccessCounter = registerCounter(meterRegistry, SENTENCE_DATES_METRIC, "The number of successful sentence date updates ", SUCCESS_TYPE)
  private val statusChangesTotalCounter = registerCounter(meterRegistry, STATUS_CHANGE_METRIC, "The number of status change updates received", SUCCESS_TYPE)
  private val statusChangesFailedCounter = registerCounter(meterRegistry, STATUS_CHANGE_METRIC, "The number of failed status change updates ", FAIL_TYPE)
  private val statusChangesSuccessCounter = registerCounter(meterRegistry, STATUS_CHANGE_METRIC, "The number of successful status change updates ", SUCCESS_TYPE)

  private fun registerCounter(meterRegistry: MeterRegistry, name: String, description: String, type: String): Counter {
    val builder = Counter.builder(name).description(description)
    builder.tag("type", type)
    return builder.register(meterRegistry)
  }

  fun movementReceived() = movementReceivedCounter.increment()
  fun movementFailed() = movementsFailedCounter.increment()
  fun movementSucceeded() = movementsSuccessCounter.increment()

  fun sentenceDateChangeReceived() = sentenceDatesTotalCounter.increment()
  fun sentenceDateChangeFailed() = sentenceDatesFailedCounter.increment()
  fun sentenceDateChangeSucceeded() = sentenceDatesSuccessCounter.increment()

  fun statusChangeReceived() = sentenceDatesTotalCounter.increment()
  fun statusChangeFailed() = sentenceDatesFailedCounter.increment()
  fun statusChangeSucceeded() = sentenceDatesSuccessCounter.increment()
}
