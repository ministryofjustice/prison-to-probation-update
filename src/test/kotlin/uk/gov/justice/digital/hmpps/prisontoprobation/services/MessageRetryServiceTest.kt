package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.lang.RuntimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

internal class MessageRetryServiceTest {
  private val messageProcessor: MessageProcessor = mock()
  private val messageRepository: MessageRepository = mock()
  private var service: MessageRetryService = MessageRetryService(messageRepository, messageProcessor, 168)

  @BeforeEach
  fun setUp() {
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(Done())
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf())
  }

  @Test
  internal fun `will add a retry message that expires in 7 days`() {
    service.retryLater(99L, "EVENT", "message")

    verify(messageRepository).save<Message>(check{
      assertThat(it.bookingId).isEqualTo(99L)
      assertThat(it.eventType).isEqualTo("EVENT")
      assertThat(it.message).isEqualTo("message")
      assertThat(it.retryCount).isEqualTo(1)
      assertThat(it.createdDate.toLocalDate()).isToday()
      assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.now().plusDays(7))
    })

  }
  @Test
  internal fun `will schedule a message that expires in 7 days`() {
    service.scheduleForProcessing(99L, "EVENT", "message")

    verify(messageRepository).save<Message>(check{
      assertThat(it.bookingId).isEqualTo(99L)
      assertThat(it.eventType).isEqualTo("EVENT")
      assertThat(it.message).isEqualTo("message")
      assertThat(it.retryCount).isEqualTo(0)
      assertThat(it.createdDate.toLocalDate()).isToday()
      assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.now().plusDays(7))
    })

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
  internal fun `will update retry count of failure`() {
    val message = Message(bookingId = 99L, message = "{}", id = "123", retryCount = 1)
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(RetryLater(bookingId = 99L))

    service.retryShortTerm()

    verify(messageRepository).save<Message>(check {
      assertThat(it.id).isEqualTo("123")
      assertThat(it.retryCount).isEqualTo(2)
    })
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
}