package uk.gov.justice.digital.hmpps.prisontoprobation.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset


@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class MessageRepositoryTest {

  @Autowired
  private lateinit var repository: MessageRepository

  @BeforeEach
  fun setup() {
    repository.deleteAll()
  }

  @Test
  internal fun canWriteToRepository() {
    repository.save(Message(bookingId = 99L, eventType = "IMPRISONMENT_STATUS-CHANGED", message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"))

    val message = repository.findAll().first()

    assertThat(message.bookingId).isEqualTo(99L)
    assertThat(message.retryCount).isEqualTo(1)
    assertThat(message.createdDate.toLocalDate()).isToday()
    assertThat(message.eventType).isEqualTo("IMPRISONMENT_STATUS-CHANGED")
    assertThat(message.message).isEqualTo("{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}")

    assertThat(LocalDateTime.ofEpochSecond(message.deleteBy, 0, ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.now().plusDays(7))
  }

  @Test
  internal fun canQueryRepository() {
    repository.save(Message(bookingId = 99L, retryCount = 1, createdDate = LocalDateTime.now(), eventType = "IMPRISONMENT_STATUS-CHANGED", message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"))
    repository.save(Message(bookingId = 99L, retryCount = 1, createdDate = LocalDateTime.now(), eventType = "IMPRISONMENT_STATUS-CHANGED", message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"))
    repository.save(Message(bookingId = 100L, retryCount = 2, createdDate = LocalDateTime.now(), eventType = "IMPRISONMENT_STATUS-CHANGED", message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"))
    repository.save(Message(bookingId = 100L, retryCount = 3, createdDate = LocalDateTime.now(), eventType = "EXTERNAL_MOVEMENT_RECORD-INSERTED", message = "{\"eventType\":\"EXTERNAL_MOVEMENT_RECORD-INSERTED\",\"eventDatetime\":\"2020-01-13T11:33:23.790725\",\"bookingId\":1200835,\"movementSeq\":1,\"nomisEventType\":\"M1_RESULT\"}"))

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


    assertThat(repository.findByRetryCountBetween(1, 2))
        .hasSize(3)
        .extracting<Long>(Message::bookingId).containsExactlyInAnyOrder(99L, 99L, 100L)

    assertThat(repository.findByRetryCountBetween(1, 3))
        .hasSize(4)
        .extracting<Long>(Message::bookingId).containsExactlyInAnyOrder(99L, 99L, 100L, 100L)

    assertThat(repository.findByRetryCountBetween(3, Int.MAX_VALUE))
        .hasSize(1)
        .extracting<Long>(Message::bookingId).containsExactly(100L)

  }

  @Test
  internal fun canUpdateAMessageRetryCount() {
    repository.save(Message(bookingId = 99L, retryCount = 1, createdDate = LocalDateTime.now(), eventType = "IMPRISONMENT_STATUS-CHANGED", message = "{\"eventType\":\"IMPRISONMENT_STATUS-CHANGED\",\"eventDatetime\":\"2020-02-12T15:14:24.125533\",\"bookingId\":1200835,\"nomisEventType\":\"OFF_IMP_STAT_OASYS\"}"))

    val message = repository.findAll().first()
    assertThat(message.retryCount).isEqualTo(1)

    repository.save(message.retry())

    val messageAfterRetry = repository.findAll().first()
    assertThat(messageAfterRetry.retryCount).isEqualTo(2)
  }

}