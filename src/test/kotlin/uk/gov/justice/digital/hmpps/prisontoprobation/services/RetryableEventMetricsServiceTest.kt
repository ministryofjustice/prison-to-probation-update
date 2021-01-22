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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
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
    private val successTimer = mock<DistributionSummary>()

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
    fun `Counts number of days required to process a success event`() {
      val metricService = RetryableEventMetricsService(meterRegistry, meterFactory)

      metricService.eventSucceeded("IMPRISONMENT_STATUS-CHANGED", LocalDateTime.now().minusHours(36L))

      verify(successTimer).record(2.0)
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
