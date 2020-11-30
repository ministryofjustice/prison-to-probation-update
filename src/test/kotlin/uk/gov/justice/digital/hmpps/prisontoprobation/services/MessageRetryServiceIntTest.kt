package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
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
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any())).thenReturn(Done())
    service.retryLater(bookingId = 33L, eventType = eventType, message = message)

    // continually call this as if on a schedule
    repeat(33) {
      service.retryShortTerm()
    }

    verify(messageProcessor, times(1)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo(eventType)
        assertThat(it.message).isEqualTo(message)
      }
    )
  }

  @Test
  internal fun `will retry twice on success on second attempt`() {
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any()))
      .thenReturn(TryLater(1200835))
      .thenReturn(Done())
    service.retryLater(bookingId = 33L, eventType = eventType, message = message)

    // continually call this as if on a schedule
    repeat(33) {
      service.retryShortTerm()
    }

    verify(messageProcessor, times(2)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo(eventType)
        assertThat(it.message).isEqualTo(message)
      }
    )
  }

  @Test
  internal fun `will retry four times in short term retry mode on failure`() {
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any())).thenReturn(TryLater(1200835))
    service.retryLater(bookingId = 33L, eventType = eventType, message = message)

    // continually call this as if on a schedule
    repeat(33) {
      service.retryShortTerm()
    }

    verify(messageProcessor, times(4)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo(eventType)
        assertThat(it.message).isEqualTo(message)
      }
    )
  }

  @Test
  internal fun `will retry six times in medium term retry mode on failure`() {
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any())).thenReturn(TryLater(1200835))
    service.retryLater(bookingId = 33L, eventType = eventType, message = message)
    val shortTermRepeat = 4

    repeat(shortTermRepeat) {
      service.retryShortTerm()
    }
    repeat(33) {
      service.retryMediumTerm()
    }

    verify(messageProcessor, times(6 + shortTermRepeat)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo(eventType)
        assertThat(it.message).isEqualTo(message)
      }
    )
  }

  @Test
  internal fun `will retry until deleted in long term retry mode on failure`() {
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    whenever(messageProcessor.processMessage(any())).thenReturn(TryLater(1200835))
    service.retryLater(bookingId = 33L, eventType = eventType, message = message)
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

    verify(messageProcessor, times(33 + shortTermRepeat + mediumTermRepeat)).processMessage(
      check {
        assertThat(it.eventType).isEqualTo(eventType)
        assertThat(it.message).isEqualTo(message)
      }
    )
  }
}
