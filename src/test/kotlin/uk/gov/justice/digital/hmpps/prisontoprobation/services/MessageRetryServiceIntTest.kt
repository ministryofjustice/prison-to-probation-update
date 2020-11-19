package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
internal class MessageRetryServiceIntTest {
  @Autowired
  private lateinit var service: MessageRetryService
  @Autowired
  private lateinit var repository: MessageRepository

  @MockBean
  private lateinit var messageProcessor: MessageProcessor

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  @Test
  internal fun `will retry once on success`() {
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(Done())
    service.retryLater(bookingId = 33L, eventType = "IMPRISONMENT_STATUS-CHANGED", message = message)

    // continually call this as if on a schedule
    repeat(33) {
      service.retryShortTerm()
    }

    verify(messageProcessor, times(1)).processMessage("IMPRISONMENT_STATUS-CHANGED", message)
  }

  @Test
  internal fun `will retry twice on success on second attempt`() {
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any(), any()))
      .thenReturn(TryLater(1200835))
      .thenReturn(Done())
    service.retryLater(bookingId = 33L, eventType = "IMPRISONMENT_STATUS-CHANGED", message = message)

    // continually call this as if on a schedule
    repeat(33) {
      service.retryShortTerm()
    }

    verify(messageProcessor, times(2)).processMessage("IMPRISONMENT_STATUS-CHANGED", message)
  }

  @Test
  internal fun `will retry four times in short term retry mode on failure`() {
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(TryLater(1200835))
    service.retryLater(bookingId = 33L, eventType = "IMPRISONMENT_STATUS-CHANGED", message = message)

    // continually call this as if on a schedule
    repeat(33) {
      service.retryShortTerm()
    }

    verify(messageProcessor, times(4)).processMessage("IMPRISONMENT_STATUS-CHANGED", message)
  }

  @Test
  internal fun `will retry six times in medium term retry mode on failure`() {
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(TryLater(1200835))
    service.retryLater(bookingId = 33L, eventType = "IMPRISONMENT_STATUS-CHANGED", message = message)
    val shortTermRepeat = 4

    repeat(shortTermRepeat) {
      service.retryShortTerm()
    }
    repeat(33) {
      service.retryMediumTerm()
    }

    verify(messageProcessor, times(6 + shortTermRepeat)).processMessage("IMPRISONMENT_STATUS-CHANGED", message)
  }

  @Test
  internal fun `will retry until deleted in long term retry mode on failure`() {
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(TryLater(1200835))
    service.retryLater(bookingId = 33L, eventType = "IMPRISONMENT_STATUS-CHANGED", message = message)
    val shortTermRepeat = 4
    val mediumTermRepeat = 6

    repeat(shortTermRepeat) {
      service.retryShortTerm()
    }
    repeat(mediumTermRepeat) {
      service.retryMediumTerm()
    }

    repeat(33) {
      service.retryLongTerm()
    }

    verify(messageProcessor, times(33 + shortTermRepeat + mediumTermRepeat)).processMessage("IMPRISONMENT_STATUS-CHANGED", message)
  }
}
