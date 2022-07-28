package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.LocalDate

class HMPPSPrisonerChangesListenerPusherTest {
  private val communityService: CommunityService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val releaseAndRecallService = ReleaseAndRecallService(communityService, telemetryClient)
  private lateinit var pusher: HMPPSPrisonerChangesListenerPusher
  private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().apply {
    this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    this.registerModule(JavaTimeModule())
  }

  @BeforeEach
  fun before() {
    pusher = HMPPSPrisonerChangesListenerPusher(releaseAndRecallService, objectMapper, telemetryClient)
  }

  @Nested
  inner class PrisonerReceivedEvents {

    @Test
    fun `will call community api for a prisoner received admission event`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerRecalled.json".readResourceAsText())
      verify(communityService).prisonerRecalled("A5194DY", "MDI", LocalDate.of(2020, 2, 12), "RECALL", "ADMISSION")
    }

    @Test
    fun `will not call community api for a prisoner received event we are not interested in`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReturnedFromCourt.json".readResourceAsText())
      verifyNoMoreInteractions(communityService)
    }
  }

  @Nested
  inner class PrisonerReleasedEvents {

    @Test
    fun `will call community api for a prisoner released event`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReleased.json".readResourceAsText())
      verify(communityService).prisonerReleased("A5194DY", "MDI", LocalDate.of(2020, 2, 12), "RELEASED")
    }

    @Test
    fun `will call community api for a prisoner released to hospital event`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReleasedToHospital.json".readResourceAsText())
      verify(communityService).prisonerReleased("A5194DY", "MDI", LocalDate.of(2020, 2, 12), "RELEASED_TO_HOSPITAL")
    }

    @Test
    fun `will call community api for a prisoner released on temporary absence`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReleasedOnTemporaryAbsence.json".readResourceAsText())
      verify(communityService).prisonerReleased("A5194DY", "MDI", LocalDate.of(2020, 2, 12), "TEMPORARY_ABSENCE_RELEASE")
    }

    @Test
    fun `will not call community api for a prisoner released event we are not interested in`() {
      pusher.pushHMPPSPrisonUpdateToProbation("/messages/prisonerReleasedUnknown.json".readResourceAsText())
      verifyNoMoreInteractions(communityService)
    }
  }

  @Test
  internal fun `will not call service for events we don't understand`() {
    pusher.pushHMPPSPrisonUpdateToProbation("/messages/imprisonmentStatusChanged.json".readResourceAsText())
    verifyNoMoreInteractions(communityService)
  }

  @Nested
  inner class OccurredDateFormat {
    @Test
    @DisplayName("can read dates in iso local date format")
    internal fun `can read dates in local date format`() {
      pusher.pushHMPPSPrisonUpdateToProbation(messageThatOccurredAt("2020-02-12T15:14:24.125533"))
      verify(communityService).prisonerRecalled("A5194DY", "MDI", LocalDate.of(2020, 2, 12), "RECALL", "ADMISSION")
    }

    @Test
    internal fun `can read dates in iso offset format`() {
      pusher.pushHMPPSPrisonUpdateToProbation(messageThatOccurredAt("2020-02-12T15:14:24.125533Z"))
      verify(communityService).prisonerRecalled("A5194DY", "MDI", LocalDate.of(2020, 2, 12), "RECALL", "ADMISSION")
    }

    @Test
    internal fun `can read dates in iso offset format in the summer`() {
      pusher.pushHMPPSPrisonUpdateToProbation(messageThatOccurredAt("2020-07-12T15:14:24.125533+01:00"))
      verify(communityService).prisonerRecalled("A5194DY", "MDI", LocalDate.of(2020, 7, 12), "RECALL", "ADMISSION")
    }
  }
}

private fun String.readResourceAsText(): String {
  return MessageProcessorTest::class.java.getResource(this)?.readText() ?: throw AssertionError("can not find file")
}

private fun messageThatOccurredAt(occurredAt: String) = """
  {
    "Type": "Notification",
    "MessageId": "ee46cb90-a2de-57bf-86ba-9d2eba64645a",
    "TopicArn": "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message":"{\"version\":\"1.0\", \"occurredAt\":\"$occurredAt\", \"publishedAt\":\"2020-02-12T15:15:09.902048716Z\", \"description\":\"A prisoner has been received into prison\", \"additionalInformation\":{\"nomsNumber\":\"A5194DY\", \"prisonId\":\"MDI\", \"reason\":\"ADMISSION\", \"probableCause\":\"RECALL\", \"source\":\"PROBATION\", \"details\":\"Recall referral date 2021-05-12\"}}",
    "Timestamp": "2020-02-12T15:15:06.239Z",
    "SignatureVersion": "1",
    "Signature": "E0oesISQOBGaDjgOg3wEFfCFcIMNN4GyOdCtLRuhXB8QOzFt5XhzhfhcypPyXvIN+G5+Ky79BK0SlXDWxv9vSw2tOSojNwH1vvbXApInAiqyAgIBNYgUk3l1MzKmkqoH5lWmgmo5U4szk5jKbL0LVVc4BYRY6pIq2ZWt4pPoX47Z5oibjfXZZhKsR6k5VCTnUD7lqa2hkWWqaqZIsoeCG5g83Xb5d7s+LlN5iV74gwP/lgZT0E/uSnRCk8Nx0UUPEvpk/04V5yaW6W9YP/hwKMNep873tYzTcFGilyKoU5ucy4vVMulwT+EL3iOmumQEoFcCd/BQotjU2+wQ4wL3/Q==",
    "SigningCertURL": "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-a86cb10b4e1f29c941702d737128f7b6.pem",
    "UnsubscribeURL": "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:92545cfe-de5d-43e1-8339-c366bf0172aa",
    "MessageAttributes": {
      "eventType": {
        "Type": "String",
        "Value": "prison-offender-events.prisoner.received"
      },
      "id": {
        "Type": "String",
        "Value": "11c19083-520d-5d7e-c91f-938a7b214ef2"
      },
      "contentType": {
        "Type": "String",
        "Value": "text/plain;charset=UTF-8"
      },
      "timestamp": {
        "Type": "Number.java.lang.Long",
        "Value": "1581520506234"
      }
    }
  }

""".trimIndent()
