package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

internal class MessageRetryServiceTest {
  private val messageProcessor: MessageProcessor = mock()
  private val messageRepository: MessageRepository = mock()
  private val offenderService: OffenderService = mock()
  private var service: MessageRetryService =
    MessageRetryService(messageRepository, messageProcessor, 192, offenderService)

  @BeforeEach
  fun setUp() {
    whenever(messageProcessor.processMessage(any())).thenReturn(Done())
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf())
  }

  @Test
  internal fun `will schedule a message that expires in 8 days`() {
    whenever(offenderService.getBooking(99L)).thenReturn(
      createBooking(
        agencyId = "MDI",
        locationDescription = "HMP Moorland",
        offenderNo = "AB1234Y",
        bookingNo = "12344G",
        recall = true,
        legalStatus = "SENTENCED"
      )
    )
    service.scheduleForProcessing(99L, "EVENT", "message")

    verify(messageRepository).save(
      check {
        assertThat(it.bookingId).isEqualTo(99L)
        assertThat(it.eventType).isEqualTo("EVENT")
        assertThat(it.message).isEqualTo("message")
        assertThat(it.retryCount).isEqualTo(0)
        assertThat(it.createdDate.toLocalDate()).isToday
        assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(
          LocalDate.now().plusDays(8)
        )
        assertThat(it.reportable).isTrue
        assertThat(it.processedDate).isNull()
        assertThat(it.offenderNo).isEqualTo("AB1234Y")
        assertThat(it.bookingNo).isEqualTo("12344G")
        assertThat(it.locationId).isEqualTo("MDI")
        assertThat(it.locationDescription).isEqualTo("HMP Moorland")
        assertThat(it.recall).isTrue
        assertThat(it.legalStatus).isEqualTo("SENTENCED")
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
    whenever(messageProcessor.processMessage(any())).thenReturn(
      TryLater(
        bookingId = 99L,
        retryUntil = null
      )
    )

    service.retryShortTerm()

    verify(messageRepository).save(
      check {
        assertThat(it.id).isEqualTo("123")
        assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(
          LocalDate.now().plusDays(6)
        )
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
    whenever(messageProcessor.processMessage(any())).thenReturn(
      TryLater(
        bookingId = 99L,
        retryUntil = expectedRetryUntilDate
      )
    )

    service.retryShortTerm()

    verify(messageRepository).save(
      check {
        assertThat(it.id).isEqualTo("123")
        assertThat(LocalDateTime.ofEpochSecond(it.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(
          expectedRetryUntilDate
        )
      }
    )
  }

  @Test
  internal fun `will update retry count of failure`() {
    val message = Message(bookingId = 99L, message = "{}", id = "123", retryCount = 1)
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    whenever(messageProcessor.processMessage(any())).thenReturn(TryLater(bookingId = 99L))

    service.retryShortTerm()

    verify(messageRepository).save(
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
    whenever(messageProcessor.processMessage(any())).thenThrow(RuntimeException("it has all gone wrong"))

    service.retryShortTerm()

    verify(messageProcessor, times(2)).processMessage(any())
  }

  @Test
  internal fun `will delete message on success`() {
    val message = Message(bookingId = 99L, message = "{}", id = "123", retryCount = 1)
    whenever(messageRepository.findByRetryCountBetween(any(), any())).thenReturn(listOf(message))
    whenever(messageProcessor.processMessage(any())).thenReturn(Done())

    service.retryShortTerm()

    verify(messageRepository).delete(message)
  }
}
