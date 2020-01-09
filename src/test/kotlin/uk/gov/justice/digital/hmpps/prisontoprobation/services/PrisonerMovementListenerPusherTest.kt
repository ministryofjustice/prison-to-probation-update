package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.mock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrisonerMovementListenerPusherTest {
  private val offenderService: OffenderService = mock()
  private val communityService: CommunityService = mock()

  private lateinit var listener: PrisonerMovementListenerPusher

  private val validPrisonMovement = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": 
      "{\"offenderId\":\"AB123D\"}", 
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {"eventType": {"Type": "String", "Value": "KA-KE"}, 
    "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
    "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
    "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}}}""".trimIndent()


  @Before
  fun before() {
    listener = PrisonerMovementListenerPusher(offenderService, communityService)
  }

  @Test
  fun `does nothing`() {
    listener.pushPrisonMovementToProbation(validPrisonMovement)
  }

}
