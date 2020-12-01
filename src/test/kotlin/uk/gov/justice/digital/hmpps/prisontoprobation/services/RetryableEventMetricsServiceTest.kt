package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentMatchers.anyString
import java.time.Duration
import java.time.LocalDateTime

class RetryableEventMetricsServiceTest {

  private val meterFactory = mock<MeterFactory>()
  private val meterRegistry = mock<MeterRegistry>()

  @Nested
  inner class ImprisonmentStatusChange {
    private val totalCounter = mock<Counter>()
    private val successCounter = mock<Counter>()
    private val failCounter = mock<Counter>()
    private val retryDistribution = mock<DistributionSummary>()
    private val successTimer = mock<Timer>()

    @BeforeEach
    fun `mock counters`() {
      whenever(meterFactory.registerCounter(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(TOTAL_TYPE)))
        .thenReturn(totalCounter)
      whenever(meterFactory.registerCounter(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(SUCCESS_TYPE)))
        .thenReturn(successCounter)
      whenever(meterFactory.registerCounter(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(FAIL_TYPE)))
        .thenReturn(failCounter)
      whenever(
        meterFactory.registerRetryDistribution(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(SUCCESS_AFTER_RETRIES_TYPE))
      ).thenReturn(retryDistribution)
      whenever(meterFactory.registerMessageAgeTimer(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(SUCCESS_AFTER_TIME_TYPE)))
        .thenReturn(successTimer)
    }

    @Test
    fun `Counts imprisonment status success events`() {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventSucceeded("IMPRISONMENT_STATUS-CHANGED", LocalDateTime.now())

      verify(totalCounter).increment()
      verify(successCounter).increment()
    }

    @Test
    fun `Counts number of retries required to process a success event`() {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventSucceeded("IMPRISONMENT_STATUS-CHANGED", LocalDateTime.now(), retries = 2)

      verify(retryDistribution).record(2.0)
    }

    @Test
    fun `Counts number of seconds required to process a success event`() {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventSucceeded("IMPRISONMENT_STATUS-CHANGED", LocalDateTime.now().minusMinutes(123L))

      verify(successTimer).record(Duration.ofMinutes(123L))
    }

    @Test
    fun `Counts imprisonment status fail events`() {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventFailed("IMPRISONMENT_STATUS-CHANGED", LocalDateTime.now())

      verify(totalCounter).increment()
      verify(failCounter).increment()
      verifyNoMoreInteractions(retryDistribution)
      verifyNoMoreInteractions(successTimer)
    }
  }

  @Nested
  inner class SentenceDatesChange {
    private val totalCounter = mock<Counter>()
    private val successCounter = mock<Counter>()
    private val failCounter = mock<Counter>()
    private val retryDistribution = mock<DistributionSummary>()
    private val successTimer = mock<Timer>()

    @BeforeEach
    fun `mock counters`() {
      whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(TOTAL_TYPE)))
        .thenReturn(totalCounter)
      whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(SUCCESS_TYPE)))
        .thenReturn(successCounter)
      whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(FAIL_TYPE)))
        .thenReturn(failCounter)
      whenever(
        meterFactory.registerRetryDistribution(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(SUCCESS_AFTER_RETRIES_TYPE))
      ).thenReturn(retryDistribution)
      whenever(meterFactory.registerMessageAgeTimer(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(SUCCESS_AFTER_TIME_TYPE)))
        .thenReturn(successTimer)
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED")
    fun `Counts sentence dates success events`(eventType: String) {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventSucceeded(eventType, LocalDateTime.now())

      verify(totalCounter).increment()
      verify(successCounter).increment()
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED")
    fun `Counts number of retries required to process a success event`(eventType: String) {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventSucceeded(eventType, LocalDateTime.now(), retries = 3)

      verify(retryDistribution).record(3.0)
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED")
    fun `Counts number of seconds required to process a success event`(eventType: String) {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventSucceeded(eventType, LocalDateTime.now().minusMinutes(123L))

      verify(successTimer).record(Duration.ofMinutes(123L))
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED")
    fun `Counts sentence dates fail events`(eventType: String) {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventFailed(eventType, LocalDateTime.now())

      verify(totalCounter).increment()
      verify(failCounter).increment()
      verifyNoMoreInteractions(retryDistribution)
      verifyNoMoreInteractions(successTimer)
    }
  }

  @Nested
  inner class PrisonMovement {

    private val movementTotalCounter = mock<Counter>()
    private val movementSuccessCounter = mock<Counter>()
    private val movementFailCounter = mock<Counter>()

    @BeforeEach
    fun `mock counters`() {
      whenever(meterFactory.registerCounter(any(), eq(MOVEMENT_METRIC), anyString(), eq(TOTAL_TYPE)))
        .thenReturn(movementTotalCounter)
      whenever(meterFactory.registerCounter(any(), eq(MOVEMENT_METRIC), anyString(), eq(SUCCESS_TYPE)))
        .thenReturn(movementSuccessCounter)
      whenever(meterFactory.registerCounter(any(), eq(MOVEMENT_METRIC), anyString(), eq(FAIL_TYPE)))
        .thenReturn(movementFailCounter)
    }

    @Test
    fun `Does nothing on success`() {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventSucceeded("EXTERNAL_MOVEMENT_RECORD-INSERTED", LocalDateTime.now())

      verifyNoMoreInteractions(movementTotalCounter)
      verifyNoMoreInteractions(movementSuccessCounter)
      verifyNoMoreInteractions(movementFailCounter)
    }

    @Test
    fun `Does nothing on fail`() {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventFailed("EXTERNAL_MOVEMENT_RECORD-INSERTED", LocalDateTime.now())

      verifyNoMoreInteractions(movementTotalCounter)
      verifyNoMoreInteractions(movementSuccessCounter)
      verifyNoMoreInteractions(movementFailCounter)
    }
  }
}
