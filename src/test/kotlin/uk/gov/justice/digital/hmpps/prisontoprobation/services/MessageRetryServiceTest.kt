package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.nhaarman.mockito_kotlin.check
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message

internal class MessageRetryServiceTest {
  private val messageProcessor: MessageProcessor = mock()
  private val messageRepository: MessageRepository = mock()
  private var service: MessageRetryService = MessageRetryService(messageRepository, messageProcessor)

  @BeforeEach
  fun setUp() {
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(Done())
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf())
  }

  @Test
  internal fun retryShortTermWillTryOneToFourRetryAttempts() {
    service.retryShortTerm()
    verify(messageRepository).findByRetryCountBetween(1, 4)
  }

  @Test
  internal fun retryMediumTermWillTryFiveToElevenRetryAttempts() {
    service.retryMediumTerm()
    verify(messageRepository).findByRetryCountBetween(5, 10)
  }

  @Test
  internal fun retryLongTermWillTryAllThoseAboveEleven() {
    service.retryLongTerm()
    verify(messageRepository).findByRetryCountBetween(11, 2147483647)
  }

  @Test
  internal fun willUpdateRetryCountOfFailure() {
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
  internal fun willDeleteMessageOnSuccess() {
    val message = Message(bookingId = 99L, message = "{}", id = "123", retryCount = 1)
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    whenever(messageProcessor.processMessage(any(), any())).thenReturn(Done())

    service.retryShortTerm()

    verify(messageRepository).delete(message)
  }
}