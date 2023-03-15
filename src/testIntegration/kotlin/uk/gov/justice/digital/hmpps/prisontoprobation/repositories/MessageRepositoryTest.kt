package uk.gov.justice.digital.hmpps.prisontoprobation.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class MessageRepositoryTest : NoQueueListenerIntegrationTest() {

  @Test
  internal fun canWriteToRepositoryWithBasicAttributes() {
    messageRepository.save(
      Message(
        bookingId = 99L,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
      ),
    )

    val message = messageRepository.findAll().first()

    assertThat(message.bookingId).isEqualTo(99L)
    assertThat(message.retryCount).isEqualTo(1)
    assertThat(message.createdDate.toLocalDate()).isToday
    assertThat(message.eventType).isEqualTo("IMPRISONMENT_STATUS-CHANGED")
    assertThat(message.message).isEqualTo("{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}")

    assertThat(LocalDateTime.ofEpochSecond(message.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(
      LocalDate.now().plusDays(30),
    )

    assertThat(message.processedDate).isNull()
    assertThat(message.reportable).isFalse
    assertThat(message.offenderNo).isNull()
    assertThat(message.bookingNo).isNull()
    assertThat(message.locationId).isNull()
    assertThat(message.locationDescription).isNull()
    assertThat(message.recall).isNull()
    assertThat(message.legalStatus).isNull()
  }

  @Test
  internal fun canWriteToRepositoryWithAdvancedAttributes() {
    messageRepository.save(
      Message(
        bookingId = 99L,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        processedDate = LocalDateTime.now(),
        reportable = true,
        offenderNo = "AB1235Y",
        bookingNo = "12345B",
        locationId = "MDI",
        locationDescription = "HMP Moorland",
        recall = true,
        legalStatus = "SENTENCED",
      ),
    )

    val message = messageRepository.findAll().first()

    assertThat(message.bookingId).isEqualTo(99L)
    assertThat(message.retryCount).isEqualTo(1)
    assertThat(message.createdDate.toLocalDate()).isToday
    assertThat(message.eventType).isEqualTo("IMPRISONMENT_STATUS-CHANGED")
    assertThat(message.message).isEqualTo("{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}")

    assertThat(LocalDateTime.ofEpochSecond(message.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(
      LocalDate.now().plusDays(30),
    )

    assertThat(message.processedDate?.toLocalDate()).isToday
    assertThat(message.reportable).isTrue
    assertThat(message.offenderNo).isEqualTo("AB1235Y")
    assertThat(message.bookingNo).isEqualTo("12345B")
    assertThat(message.locationId).isEqualTo("MDI")
    assertThat(message.locationDescription).isEqualTo("HMP Moorland")
    assertThat(message.recall).isTrue
    assertThat(message.legalStatus).isEqualTo("SENTENCED")
  }

  @Test
  internal fun canQueryRepository() {
    messageRepository.save(
      Message(
        bookingId = 99L,
        retryCount = 1,
        createdDate = LocalDateTime.now(),
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
      ),
    )
    messageRepository.save(
      Message(
        bookingId = 99L,
        retryCount = 1,
        createdDate = LocalDateTime.now(),
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
      ),
    )
    messageRepository.save(
      Message(
        bookingId = 100L,
        retryCount = 2,
        createdDate = LocalDateTime.now(),
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
      ),
    )
    messageRepository.save(
      Message(
        bookingId = 100L,
        retryCount = 3,
        createdDate = LocalDateTime.now(),
        eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED",
        message = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}",
      ),
    )

    assertThat(messageRepository.findAll()).hasSize(4)
    assertThat(messageRepository.findByEventTypeAndRetryCount("IMPRISONMENT_STATUS-CHANGED", 1))
      .hasSize(2)
      .extracting<Long>(Message::bookingId).containsExactly(99L, 99L)
    assertThat(messageRepository.findByEventTypeAndRetryCount("EXTERNAL_MOVEMENT_RECORD-INSERTED", 1))
      .hasSize(0)
    assertThat(messageRepository.findByEventTypeAndRetryCount("IMPRISONMENT_STATUS-CHANGED", 2))
      .hasSize(1).extracting<Long>(Message::bookingId)
      .containsExactly(100L)
    assertThat(messageRepository.findByEventTypeAndRetryCount("EXTERNAL_MOVEMENT_RECORD-INSERTED", 3))
      .hasSize(1).extracting<Long>(Message::bookingId)
      .containsExactly(100L)

    assertThat(messageRepository.findByBookingIdAndEventType(99L, "IMPRISONMENT_STATUS-CHANGED"))
      .hasSize(2)
      .extracting<Long>(Message::bookingId).containsExactly(99L, 99L)
    assertThat(messageRepository.findByBookingIdAndEventType(99L, "EXTERNAL_MOVEMENT_RECORD-INSERTED"))
      .hasSize(0)
    assertThat(messageRepository.findByBookingIdAndEventType(100L, "IMPRISONMENT_STATUS-CHANGED"))
      .hasSize(1)
      .extracting<Long>(Message::bookingId).containsExactly(100L)
    assertThat(messageRepository.findByBookingIdAndEventType(100L, "EXTERNAL_MOVEMENT_RECORD-INSERTED"))
      .hasSize(1)
      .extracting<Long>(Message::bookingId).containsExactly(100L)

    assertThat(messageRepository.findByRetryCountBetweenAndProcessedDateIsNull(1, 2))
      .hasSize(3)
      .extracting<Long>(Message::bookingId).containsExactlyInAnyOrder(99L, 99L, 100L)

    assertThat(messageRepository.findByRetryCountBetweenAndProcessedDateIsNull(1, 3))
      .hasSize(4)
      .extracting<Long>(Message::bookingId).containsExactlyInAnyOrder(99L, 99L, 100L, 100L)

    assertThat(messageRepository.findByRetryCountBetweenAndProcessedDateIsNull(3, Int.MAX_VALUE))
      .hasSize(1)
      .extracting<Long>(Message::bookingId).containsExactly(100L)
  }

  @Test
  internal fun canUpdateAMessageRetryCount() {
    messageRepository.save(
      Message(
        bookingId = 99L,
        retryCount = 1,
        createdDate = LocalDateTime.now(),
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
      ),
    )

    val message = messageRepository.findAll().first()
    assertThat(message.retryCount).isEqualTo(1)

    messageRepository.save(message.retry(status = SynchroniseStatus()))

    val messageAfterRetry = messageRepository.findAll().first()
    assertThat(messageAfterRetry.retryCount).isEqualTo(2)
  }

  @Nested
  inner class FindByRetryCountAndCreatedDateBefore {
    @Test
    internal fun `will ignore messages that are too young`() {
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )

      val messages =
        messageRepository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(0, LocalDateTime.now().minusMinutes(10))

      assertThat(messages).isEmpty()
    }

    @Test
    internal fun `will find messages that are old enough`() {
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now().minusMinutes(11),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )

      val messages =
        messageRepository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(0, LocalDateTime.now().minusMinutes(10))

      assertThat(messages).hasSize(1)
    }

    @Test
    internal fun `will find all messages that are old enough`() {
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now().minusMinutes(11),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now().minusMinutes(12),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )

      val messages =
        messageRepository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(0, LocalDateTime.now().minusMinutes(10))

      assertThat(messages).hasSize(2)
    }

    @Test
    internal fun `will only find messages that have not tried`() {
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 1,
          createdDate = LocalDateTime.now().minusMinutes(11),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )

      val messages =
        messageRepository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(0, LocalDateTime.now().minusMinutes(10))

      assertThat(messages).isEmpty()
    }
  }

  @Nested
  inner class FindByBookingIdAndProcessedDateIsNull {
    @Test
    internal fun `will include messages that are young`() {
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )

      val messages = messageRepository.findByBookingIdAndProcessedDateIsNull(99L)

      assertThat(messages).hasSize(1)
    }

    @Test
    internal fun `will exclude processed messages`() {
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
          processedDate = LocalDateTime.now(),
        ),
      )

      assertThat(messageRepository.findByBookingId(99L)).hasSize(1)

      val messages = messageRepository.findByBookingIdAndProcessedDateIsNull(99L)

      assertThat(messages).isEmpty()
    }

    @Test
    internal fun `will only include messages for the specific booking`() {
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )
      messageRepository.save(
        Message(
          bookingId = 100L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )

      val messages = messageRepository.findByBookingIdAndProcessedDateIsNull(99L)

      assertThat(messages).hasSize(1)
    }

    @Test
    internal fun `will include messages regardless to retryCount`() {
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 1,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )
      messageRepository.save(
        Message(
          bookingId = 99L,
          retryCount = 2,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
        ),
      )

      val messages = messageRepository.findByBookingIdAndProcessedDateIsNull(99L)

      assertThat(messages).hasSize(3)
    }
  }

  @Nested
  inner class FindAllByStatusInAndCreatedDateLessThan {
    @Test
    internal fun `will find records of the statuses supplied`() {
      messageRepository.save(
        aMessage(1, LocalDateTime.now(), "NO_MATCH"),
      )
      messageRepository.save(
        aMessage(2, LocalDateTime.now(), "NO_MATCH_WITH_SENTENCE_DATE"),
      )
      messageRepository.save(
        aMessage(3, LocalDateTime.now(), "KEY_DATES_NOT_UPDATED"),
      )
      messageRepository.save(
        aMessage(4, LocalDateTime.now(), "NO_MATCH", processedDate = LocalDateTime.now()),
      )
      val messages = messageRepository.findAllByStatusInAndCreatedDateLessThanAndProcessedDateIsNull(
        listOf("NO_MATCH", "NO_MATCH_WITH_SENTENCE_DATE"),
        LocalDateTime.now(),
      )
      assertThat(messages.map(Message::bookingId)).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    internal fun `will find records older then the supplied date`() {
      messageRepository.save(
        aMessage(1, LocalDateTime.now(), "NO_MATCH"),
      )
      messageRepository.save(
        aMessage(2, LocalDateTime.now().minusDays(1), "NO_MATCH"),
      )
      messageRepository.save(
        aMessage(3, LocalDateTime.now().minusDays(3), "NO_MATCH"),
      )

      val messages1 = messageRepository.findAllByStatusInAndCreatedDateLessThanAndProcessedDateIsNull(
        listOf("NO_MATCH"),
        LocalDateTime.now(),
      )
      assertThat(messages1.map(Message::bookingId)).containsExactlyInAnyOrder(1L, 2L, 3L)

      val messages2 = messageRepository.findAllByStatusInAndCreatedDateLessThanAndProcessedDateIsNull(
        listOf("NO_MATCH"),
        LocalDateTime.now().minusDays(2),
      )
      assertThat(messages2.map(Message::bookingId)).containsExactlyInAnyOrder(3L)
    }

    private fun aMessage(bookingId: Long, createdDate: LocalDateTime, status: String, processedDate: LocalDateTime? = null): Message = Message(
      bookingId = bookingId,
      retryCount = 0,
      createdDate = createdDate,
      eventType = "IMPRISONMENT_STATUS-CHANGED",
      message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
      status = status,
      processedDate = processedDate,
    )
  }
}
