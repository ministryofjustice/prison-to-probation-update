package uk.gov.justice.digital.hmpps.prisontoprobation.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import uk.gov.justice.digital.hmpps.prisontoprobation.services.SynchroniseStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class MessageRepositoryTest : NoQueueListenerIntegrationTest() {

  @Autowired
  private lateinit var repository: MessageRepository

  @BeforeEach
  fun setup() {
    repository.deleteAll()
  }

  @Test
  internal fun canWriteToRepositoryWithBasicAttributes() {
    repository.save(
      Message(
        bookingId = 99L,
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
      )
    )

    val message = repository.findAll().first()

    assertThat(message.bookingId).isEqualTo(99L)
    assertThat(message.retryCount).isEqualTo(1)
    assertThat(message.createdDate.toLocalDate()).isToday
    assertThat(message.eventType).isEqualTo("IMPRISONMENT_STATUS-CHANGED")
    assertThat(message.message).isEqualTo("{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}")

    assertThat(LocalDateTime.ofEpochSecond(message.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(
      LocalDate.now().plusDays(30)
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
    repository.save(
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
        legalStatus = "SENTENCED"
      )
    )

    val message = repository.findAll().first()

    assertThat(message.bookingId).isEqualTo(99L)
    assertThat(message.retryCount).isEqualTo(1)
    assertThat(message.createdDate.toLocalDate()).isToday
    assertThat(message.eventType).isEqualTo("IMPRISONMENT_STATUS-CHANGED")
    assertThat(message.message).isEqualTo("{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}")

    assertThat(LocalDateTime.ofEpochSecond(message.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(
      LocalDate.now().plusDays(30)
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
    repository.save(
      Message(
        bookingId = 99L,
        retryCount = 1,
        createdDate = LocalDateTime.now(),
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
      )
    )
    repository.save(
      Message(
        bookingId = 99L,
        retryCount = 1,
        createdDate = LocalDateTime.now(),
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
      )
    )
    repository.save(
      Message(
        bookingId = 100L,
        retryCount = 2,
        createdDate = LocalDateTime.now(),
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
      )
    )
    repository.save(
      Message(
        bookingId = 100L,
        retryCount = 3,
        createdDate = LocalDateTime.now(),
        eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED",
        message = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"
      )
    )

    assertThat(repository.findAll()).hasSize(4)
    assertThat(repository.findByEventTypeAndRetryCount("IMPRISONMENT_STATUS-CHANGED", 1))
      .hasSize(2)
      .extracting<Long>(Message::bookingId).containsExactly(99L, 99L)
    assertThat(repository.findByEventTypeAndRetryCount("EXTERNAL_MOVEMENT_RECORD-INSERTED", 1))
      .hasSize(0)
    assertThat(repository.findByEventTypeAndRetryCount("IMPRISONMENT_STATUS-CHANGED", 2))
      .hasSize(1).extracting<Long>(Message::bookingId)
      .containsExactly(100L)
    assertThat(repository.findByEventTypeAndRetryCount("EXTERNAL_MOVEMENT_RECORD-INSERTED", 3))
      .hasSize(1).extracting<Long>(Message::bookingId)
      .containsExactly(100L)

    assertThat(repository.findByBookingIdAndEventType(99L, "IMPRISONMENT_STATUS-CHANGED"))
      .hasSize(2)
      .extracting<Long>(Message::bookingId).containsExactly(99L, 99L)
    assertThat(repository.findByBookingIdAndEventType(99L, "EXTERNAL_MOVEMENT_RECORD-INSERTED"))
      .hasSize(0)
    assertThat(repository.findByBookingIdAndEventType(100L, "IMPRISONMENT_STATUS-CHANGED"))
      .hasSize(1)
      .extracting<Long>(Message::bookingId).containsExactly(100L)
    assertThat(repository.findByBookingIdAndEventType(100L, "EXTERNAL_MOVEMENT_RECORD-INSERTED"))
      .hasSize(1)
      .extracting<Long>(Message::bookingId).containsExactly(100L)

    assertThat(repository.findByRetryCountBetweenAndProcessedDateIsNull(1, 2))
      .hasSize(3)
      .extracting<Long>(Message::bookingId).containsExactlyInAnyOrder(99L, 99L, 100L)

    assertThat(repository.findByRetryCountBetweenAndProcessedDateIsNull(1, 3))
      .hasSize(4)
      .extracting<Long>(Message::bookingId).containsExactlyInAnyOrder(99L, 99L, 100L, 100L)

    assertThat(repository.findByRetryCountBetweenAndProcessedDateIsNull(3, Int.MAX_VALUE))
      .hasSize(1)
      .extracting<Long>(Message::bookingId).containsExactly(100L)
  }

  @Test
  internal fun canUpdateAMessageRetryCount() {
    repository.save(
      Message(
        bookingId = 99L,
        retryCount = 1,
        createdDate = LocalDateTime.now(),
        eventType = "IMPRISONMENT_STATUS-CHANGED",
        message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
      )
    )

    val message = repository.findAll().first()
    assertThat(message.retryCount).isEqualTo(1)

    repository.save(message.retry(status = SynchroniseStatus()))

    val messageAfterRetry = repository.findAll().first()
    assertThat(messageAfterRetry.retryCount).isEqualTo(2)
  }

  @Nested
  inner class FindByRetryCountAndCreatedDateBefore {
    @Test
    internal fun `will ignore messages that are too young`() {
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )

      val messages =
        repository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(0, LocalDateTime.now().minusMinutes(10))

      assertThat(messages).isEmpty()
    }

    @Test
    internal fun `will find messages that are old enough`() {
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now().minusMinutes(11),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )

      val messages =
        repository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(0, LocalDateTime.now().minusMinutes(10))

      assertThat(messages).hasSize(1)
    }

    @Test
    internal fun `will find all messages that are old enough`() {
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now().minusMinutes(11),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now().minusMinutes(12),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )

      val messages =
        repository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(0, LocalDateTime.now().minusMinutes(10))

      assertThat(messages).hasSize(2)
    }

    @Test
    internal fun `will only find messages that have not tried`() {
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 1,
          createdDate = LocalDateTime.now().minusMinutes(11),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )

      val messages =
        repository.findByRetryCountAndCreatedDateBeforeAndProcessedDateIsNull(0, LocalDateTime.now().minusMinutes(10))

      assertThat(messages).isEmpty()
    }
  }

  @Nested
  inner class FindByBookingIdAndProcessedDateIsNull {
    @Test
    internal fun `will include messages that are young`() {
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )

      val messages = repository.findByBookingIdAndProcessedDateIsNull(99L)

      assertThat(messages).hasSize(1)
    }

    @Test
    internal fun `will exclude processed messages`() {
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
          processedDate = LocalDateTime.now()
        )
      )

      assertThat(repository.findByBookingId(99L)).hasSize(1)

      val messages = repository.findByBookingIdAndProcessedDateIsNull(99L)

      assertThat(messages).isEmpty()
    }

    @Test
    internal fun `will only include messages for the specific booking`() {
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )
      repository.save(
        Message(
          bookingId = 100L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )

      val messages = repository.findByBookingIdAndProcessedDateIsNull(99L)

      assertThat(messages).hasSize(1)
    }

    @Test
    internal fun `will include messages regardless to retryCount`() {
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 0,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 1,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )
      repository.save(
        Message(
          bookingId = 99L,
          retryCount = 2,
          createdDate = LocalDateTime.now(),
          eventType = "IMPRISONMENT_STATUS-CHANGED",
          message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"
        )
      )

      val messages = repository.findByBookingIdAndProcessedDateIsNull(99L)

      assertThat(messages).hasSize(3)
    }
  }

  @Nested
  inner class FindAllByStatusInAndCreatedDateLessThan {
    @Test
    internal fun `will find records of the statuses supplied`() {
      repository.save(
        aMessage(1, LocalDateTime.now(), "NO_MATCH")
      )
      repository.save(
        aMessage(2, LocalDateTime.now(), "NO_MATCH_WITH_SENTENCE_DATE")
      )
      repository.save(
        aMessage(3, LocalDateTime.now(), "KEY_DATES_NOT_UPDATED")
      )
      repository.save(
        aMessage(4, LocalDateTime.now(), "NO_MATCH", processedDate = LocalDateTime.now())
      )
      assertThat(
        repository.findAllByStatusInAndCreatedDateLessThanAndProcessedDateIsNull(
          listOf("NO_MATCH", "NO_MATCH_WITH_SENTENCE_DATE"),
          LocalDateTime.now()
        )
      ).flatExtracting(Message::bookingId).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    internal fun `will find records older then the supplied date`() {
      repository.save(
        aMessage(1, LocalDateTime.now(), "NO_MATCH")
      )
      repository.save(
        aMessage(2, LocalDateTime.now().minusDays(1), "NO_MATCH")
      )
      repository.save(
        aMessage(3, LocalDateTime.now().minusDays(3), "NO_MATCH")
      )

      assertThat(
        repository.findAllByStatusInAndCreatedDateLessThanAndProcessedDateIsNull(
          listOf("NO_MATCH"),
          LocalDateTime.now()
        )
      ).flatExtracting(Message::bookingId).containsExactlyInAnyOrder(1L, 2L, 3L)

      assertThat(
        repository.findAllByStatusInAndCreatedDateLessThanAndProcessedDateIsNull(
          listOf("NO_MATCH"),
          LocalDateTime.now().minusDays(2)
        )
      ).flatExtracting(Message::bookingId).containsExactlyInAnyOrder(3L)
    }

    private fun aMessage(bookingId: Long, createdDate: LocalDateTime, status: String, processedDate: LocalDateTime? = null): Message = Message(
      bookingId = bookingId,
      retryCount = 0,
      createdDate = createdDate,
      eventType = "IMPRISONMENT_STATUS-CHANGED",
      message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}",
      status = status,
      processedDate = processedDate
    )
  }
}
