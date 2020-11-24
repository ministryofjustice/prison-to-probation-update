package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
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

class MetricServiceTest {

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
        meterFactory.registerDistributionSummary(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(SUCCESS_AFTER_RETRIES_TYPE))
      ).thenReturn(retryDistribution)
      whenever(meterFactory.registerTimer(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(SUCCESS_AFTER_TIME_TYPE)))
        .thenReturn(successTimer)
    }

    @Test
    fun `Counts imprisonment status success events`() {
      val metricService = MetricService(meterRegistry, meterFactory)

      metricService.retryEventSuccess("IMPRISONMENT_STATUS-CHANGED")

      verify(totalCounter).increment()
      verify(successCounter).increment()
    }

    @Test
    fun `Counts number of retries required to process a success event`() {
      val metricService = MetricService(meterRegistry, meterFactory)

      metricService.retryEventSuccess("IMPRISONMENT_STATUS-CHANGED", retries = 2)

      verify(retryDistribution).record(2.0)
    }

    @Test
    fun `Counts number of seconds required to process a success event`() {
      val metricService = MetricService(meterRegistry, meterFactory)

      metricService.retryEventSuccess("IMPRISONMENT_STATUS-CHANGED", duration = Duration.ofSeconds(12345L))

      verify(successTimer).record(Duration.ofSeconds(12345L))
    }

    @Test
    fun `Counts imprisonment status fail events`() {
      val metricService = MetricService(meterRegistry, meterFactory)

      metricService.retryEventFail("IMPRISONMENT_STATUS-CHANGED")

      verify(totalCounter).increment()
      verify(failCounter).increment()
      verifyZeroInteractions(retryDistribution)
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
        meterFactory.registerDistributionSummary(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(SUCCESS_AFTER_RETRIES_TYPE))
      ).thenReturn(retryDistribution)
      whenever(meterFactory.registerTimer(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(SUCCESS_AFTER_TIME_TYPE)))
        .thenReturn(successTimer)
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED")
    fun `Counts sentence dates success events`(eventType: String) {
      val metricService = MetricService(meterRegistry, meterFactory)

      metricService.retryEventSuccess(eventType)

      verify(totalCounter).increment()
      verify(successCounter).increment()
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED")
    fun `Counts number of retries required to process a success event`(eventType: String) {
      val metricService = MetricService(meterRegistry, meterFactory)

      metricService.retryEventSuccess(eventType, retries = 3)

      verify(retryDistribution).record(3.0)
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED")
    fun `Counts number of seconds required to process a success event`(eventType: String) {
      val metricService = MetricService(meterRegistry, meterFactory)

      metricService.retryEventSuccess(eventType, duration = Duration.ofSeconds(12345L))

      verify(successTimer).record(Duration.ofSeconds(12345L))
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED")
    fun `Counts sentence dates fail events`(eventType: String) {
      val metricService = MetricService(meterRegistry, meterFactory)

      metricService.retryEventFail(eventType)

      verify(totalCounter).increment()
      verify(failCounter).increment()
      verifyZeroInteractions(retryDistribution)
      verifyZeroInteractions(successTimer)
    }
  }
}
