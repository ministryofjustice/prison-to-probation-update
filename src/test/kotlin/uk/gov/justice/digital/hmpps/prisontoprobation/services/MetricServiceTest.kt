package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString

class MetricServiceTest {

  private val meterFactory = mock<MeterFactory>()
  private val meterRegistry = mock<MeterRegistry>()

  @Test
  fun `Counts imprisonment status success events`() {
    val totalCounter = mock<Counter>()
    val successCounter = mock<Counter>()
    whenever(meterFactory.registerCounter(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(TOTAL_TYPE))).thenReturn(totalCounter)
    whenever(meterFactory.registerCounter(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(SUCCESS_TYPE))).thenReturn(successCounter)
    val meterService = MetricService(meterRegistry, meterFactory)

    meterService.retryEventSuccess("IMPRISONMENT_STATUS-CHANGED")

    verify(totalCounter).increment()
    verify(successCounter).increment()
  }

  @Test
  fun `Counts imprisonment status fail events`() {
    val totalCounter = mock<Counter>()
    val failCounter = mock<Counter>()
    whenever(meterFactory.registerCounter(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(TOTAL_TYPE))).thenReturn(totalCounter)
    whenever(meterFactory.registerCounter(any(), eq(STATUS_CHANGE_METRIC), anyString(), eq(FAIL_TYPE))).thenReturn(failCounter)
    val meterService = MetricService(meterRegistry, meterFactory)

    meterService.retryEventFail("IMPRISONMENT_STATUS-CHANGED")

    verify(totalCounter).increment()
    verify(failCounter).increment()
  }

  @Test
  fun `Counts sentence dates success events`() {
    val totalCounter = mock<Counter>()
    val successCounter = mock<Counter>()
    whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(TOTAL_TYPE))).thenReturn(totalCounter)
    whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(SUCCESS_TYPE))).thenReturn(successCounter)
    val meterService = MetricService(meterRegistry, meterFactory)

    meterService.retryEventSuccess("SENTENCE_DATES-CHANGED")

    verify(totalCounter).increment()
    verify(successCounter).increment()
  }

  @Test
  fun `Counts sentence dates fail events`() {
    val totalCounter = mock<Counter>()
    val failCounter = mock<Counter>()
    whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(TOTAL_TYPE))).thenReturn(totalCounter)
    whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(FAIL_TYPE))).thenReturn(failCounter)
    val meterService = MetricService(meterRegistry, meterFactory)

    meterService.retryEventFail("SENTENCE_DATES-CHANGED")

    verify(totalCounter).increment()
    verify(failCounter).increment()
  }

  @Test
  fun `Counts release date success events`() {
    val totalCounter = mock<Counter>()
    val successCounter = mock<Counter>()
    whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(TOTAL_TYPE))).thenReturn(totalCounter)
    whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(SUCCESS_TYPE))).thenReturn(successCounter)
    val meterService = MetricService(meterRegistry, meterFactory)

    meterService.retryEventSuccess("CONFIRMED_RELEASE_DATE-CHANGED")

    verify(totalCounter).increment()
    verify(successCounter).increment()
  }

  @Test
  fun `Counts release date fail events`() {
    val totalCounter = mock<Counter>()
    val failCounter = mock<Counter>()
    whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(TOTAL_TYPE))).thenReturn(totalCounter)
    whenever(meterFactory.registerCounter(any(), eq(SENTENCE_DATES_METRIC), anyString(), eq(FAIL_TYPE))).thenReturn(failCounter)
    val meterService = MetricService(meterRegistry, meterFactory)

    meterService.retryEventFail("CONFIRMED_RELEASE_DATE-CHANGED")

    verify(totalCounter).increment()
    verify(failCounter).increment()
  }
}
