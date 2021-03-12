package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.byLessThan
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class MessageRetryServiceIntTest : NoQueueListenerIntegrationTest() {
  @Autowired
  private lateinit var service: MessageRetryService

  @Test
  internal fun `will retry once on success`() {
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message =
      "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    doReturn(Done()).whenever(messageProcessor).processMessage(any())
    messageRepository.save(
      Message(
        bookingId = 33L,
        eventType = eventType,
        message = message,
        retryCount = 1,
        deleteBy = LocalDateTime.now().plusHours(1).toEpochSecond(
          ZoneOffset.UTC
        )
      )
    )

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
  internal fun `will keep a processed record for reporting`() {
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message =
      "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    doReturn(Done()).whenever(messageProcessor).processMessage(any())
    messageRepository.save(
      Message(
        bookingId = 33L,
        eventType = eventType,
        message = message,
        retryCount = 1,
        deleteBy = LocalDateTime.now().plusHours(1).toEpochSecond(
          ZoneOffset.UTC
        )
      )
    )

    service.retryShortTerm()

    assertThat(messageRepository.findByBookingId(33L))
      .hasSize(1)

    assertThat(messageRepository.findByBookingIdAndProcessedDateIsNull(33L))
      .hasSize(0)

    assertThat(messageRepository.findByBookingId(33L).first().processedDate)
      .isCloseToUtcNow(byLessThan(1, ChronoUnit.SECONDS))
  }

  @Test
  internal fun `will retry twice on success on second attempt`() {
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message =
      "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    doReturn(TryLater(1200835))
      .doReturn(Done())
      .whenever(messageProcessor).processMessage(any())
    messageRepository.save(
      Message(
        bookingId = 33L,
        eventType = eventType,
        message = message,
        retryCount = 1,
        deleteBy = LocalDateTime.now().plusHours(1).toEpochSecond(
          ZoneOffset.UTC
        )
      )
    )

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
    val message =
      "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    doReturn(TryLater(1200835)).whenever(messageProcessor).processMessage(any())
    messageRepository.save(
      Message(
        bookingId = 33L,
        eventType = eventType,
        message = message,
        retryCount = 1,
        deleteBy = LocalDateTime.now().plusHours(1).toEpochSecond(
          ZoneOffset.UTC
        )
      )
    )

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
    val message =
      "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    doReturn(TryLater(1200835)).whenever(messageProcessor).processMessage(any())
    messageRepository.save(
      Message(
        bookingId = 33L,
        eventType = eventType,
        message = message,
        retryCount = 1,
        deleteBy = LocalDateTime.now().plusHours(1).toEpochSecond(
          ZoneOffset.UTC
        )
      )
    )
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
    val message =
      "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    doReturn(TryLater(1200835)).whenever(messageProcessor).processMessage(any())
    messageRepository.save(
      Message(
        bookingId = 33L,
        eventType = eventType,
        message = message,
        retryCount = 1,
        deleteBy = LocalDateTime.now().plusHours(1).toEpochSecond(
          ZoneOffset.UTC
        )
      )
    )
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

  @Test
  internal fun `will not retry when marked as processed`() {
    val eventType = "IMPRISONMENT_STATUS-CHANGED"
    val message =
      "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
    doReturn(Done()).whenever(messageProcessor).processMessage(any())
    messageRepository.save(
      Message(
        bookingId = 33L,
        eventType = eventType,
        message = message,
        retryCount = 1,
        deleteBy = LocalDateTime.now().plusHours(1).toEpochSecond(
          ZoneOffset.UTC,
        ),
        processedDate = LocalDateTime.now().minusMinutes(50)
      )
    )

    service.retryShortTerm()
    service.retryMediumTerm()
    service.retryLongTerm()

    verify(messageProcessor, never()).processMessage(any())
  }
}
