package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

internal class MessageRetryServiceTest {
  private val messageProcessor: MessageProcessor = mock()
  private val messageRepository: MessageRepository = mock()
  private val metricService: MetricService = mock()
  private var service: MessageRetryService = MessageRetryService(messageRepository, messageProcessor, metricService, 192)

  @BeforeEach
  fun setUp() {
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(Done())
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf())
  }

  @Test
  internal fun `will add a retry message that expires in 8 days`() {
    service.retryLater(99L, "EVENT", "message")

    verify(messageRepository).save<Message>(
      check {
        assertThat(it.bookingId).isEqualTo(99L)
        assertThat(it.eventType).isEqualTo("EVENT")
        assertThat(it.message).isEqualTo("message")
        assertThat(it.retryCount).isEqualTo(1)
        assertThat(it.createdDate.toLocalDate()).isToday()
        assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.now().plusDays(8))
      }
    )
  }
  @Test
  internal fun `will schedule a message that expires in 8 days`() {
    service.scheduleForProcessing(99L, "EVENT", "message")

    verify(messageRepository).save<Message>(
      check {
        assertThat(it.bookingId).isEqualTo(99L)
        assertThat(it.eventType).isEqualTo("EVENT")
        assertThat(it.message).isEqualTo("message")
        assertThat(it.retryCount).isEqualTo(0)
        assertThat(it.createdDate.toLocalDate()).isToday()
        assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.now().plusDays(8))
      }
    )
  }

  @Test
  internal fun `retry short term will try one to four retry attempts`() {
    service.retryShortTerm()
    verify(messageRepository).findByRetryCountBetween(1, 4)
  }

  @Test
  internal fun `retry medium term will try five to eleven retry attempts`() {
    service.retryMediumTerm()
    verify(messageRepository).findByRetryCountBetween(5, 10)
  }

  @Test
  internal fun `retry long term will try all those above eleven`() {
    service.retryLongTerm()
    verify(messageRepository).findByRetryCountBetween(11, 2147483647)
  }

  @Test
  internal fun `will keep deleteBy as 8 days by default when trying later`() {
    val message = Message(
      bookingId = 99L,
      message = "{}",
      id = "123",
      retryCount = 1,
      deleteBy = LocalDateTime.now().plusDays(6).toEpochSecond(ZoneOffset.UTC)
    )
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(
      TryLater(
        bookingId = 99L,
        retryUntil = null
      )
    )

    service.retryShortTerm()

    verify(messageRepository).save<Message>(
      check {
        assertThat(it.id).isEqualTo("123")
        assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.now().plusDays(6))
      }
    )
  }

  @Test
  internal fun `will update deleteBy to new date if requested`() {
    val expectedRetryUntilDate = LocalDate.now().plusDays(88)
    val message = Message(
      bookingId = 99L,
      message = "{}",
      id = "123",
      retryCount = 1,
      deleteBy = LocalDateTime.now().plusDays(6).toEpochSecond(ZoneOffset.UTC)
    )
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(
      TryLater(
        bookingId = 99L,
        retryUntil = expectedRetryUntilDate
      )
    )

    service.retryShortTerm()

    verify(messageRepository).save<Message>(
      check {
        assertThat(it.id).isEqualTo("123")
        assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(expectedRetryUntilDate)
      }
    )
  }

  @Test
  internal fun `will update retry count of failure`() {
    val message = Message(bookingId = 99L, message = "{}", id = "123", retryCount = 1)
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(TryLater(bookingId = 99L))

    service.retryShortTerm()

    verify(messageRepository).save<Message>(
      check {
        assertThat(it.id).isEqualTo("123")
        assertThat(it.retryCount).isEqualTo(2)
      }
    )
  }

  @Test
  internal fun `will continue processing messages even when we encounter an error`() {
    val message1 = Message(bookingId = 99L, message = "{}", id = "123", retryCount = 1, eventType = "EVENT_A")
    val message2 = Message(bookingId = 100L, message = "{}", id = "456", retryCount = 1, eventType = "EVENT_A")
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message1, message2))
    whenever(messageProcessor.processMessage(any(), any())).thenThrow(RuntimeException("it has all gone wrong"))

    service.retryShortTerm()

    verify(messageProcessor, times(2)).processMessage(any(), any())
  }

  @Test
  internal fun `will delete message on success`() {
    val message = Message(bookingId = 99L, message = "{}", id = "123", retryCount = 1)
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(Done())

    service.retryShortTerm()

    verify(messageRepository).delete(message)
  }

  @Nested
  inner class Metrics {

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED", "IMPRISONMENT_STATUS-CHANGED")
    fun `will count successful processing of message`(eventType: String) {
      mockLongRetryMessage(deleteBy = LocalDateTime.now().plusDays(6), eventType = eventType)
      whenever(messageProcessor.processMessage(any(), any())).thenReturn(Done())

      service.retryLongTerm()

      verify(metricService).retryEventSuccess(
        eq(eventType),
        check { it >= Duration.ofDays(1L) && it < Duration.ofDays(2L) },
        eq(11)
      )
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED", "IMPRISONMENT_STATUS-CHANGED")
    fun `will ignore message if trying later`(eventType: String) {
      mockLongRetryMessage(deleteBy = LocalDateTime.now().plusHours(25), eventType = eventType)
      whenever(messageProcessor.processMessage(any(), any())).thenReturn(TryLater(bookingId = 99L))

      service.retryLongTerm()

      verifyNoMoreInteractions(metricService)
    }

    @ParameterizedTest
    @CsvSource("SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED", "IMPRISONMENT_STATUS-CHANGED")
    fun `will count failed message if within 24 hours of expiry`(eventType: String) {
      mockLongRetryMessage(deleteBy = LocalDateTime.now().plusHours(23), eventType = eventType)
      whenever(messageProcessor.processMessage(any(), any())).thenReturn(TryLater(bookingId = 99L))

      service.retryLongTerm()

      verify(metricService).retryEventFail(eventType)
    }

    private fun mockLongRetryMessage(deleteBy: LocalDateTime, eventType: String) {
      val message = Message(
        bookingId = 99L,
        message = "{}",
        id = "123",
        retryCount = 11,
        createdDate = LocalDateTime.now().minusDays(1L),
        deleteBy = deleteBy.toEpochSecond(ZoneOffset.UTC),
        eventType = eventType
      )
      whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    }
  }
}
