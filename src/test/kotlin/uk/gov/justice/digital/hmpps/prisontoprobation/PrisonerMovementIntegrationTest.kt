package uk.gov.justice.digital.hmpps.prisontoprobation

import com.amazonaws.services.sqs.AmazonSQS
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.ClassRule
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.prisontoprobation.services.health.IntegrationTest
import uk.gov.justice.digital.hmpps.whereabouts.integration.wiremock.Elite2MockServer


@ActiveProfiles("test,test-queue")
class PrisonerMovementIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var sqsClient: AmazonSQS

    @Autowired
    private lateinit var queueUrl: String

    @Test
    fun `will consume a prison movement message`() {
        val message = this::class.java.getResource("/messages/externalMovement.json").readText()

        // wait until our queue has been purged
        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

        sqsClient.sendMessage(queueUrl, message)

        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/movement/1") } matches { it == 1 }
        await untilCallTo { eliteRequestCountFor("/api/prisoners?offenderNo=A5089DY") } matches { it == 1 }
    }

    private fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
        val queueAttributes = sqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
        return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    }

    private fun eliteRequestCountFor(url: String): Int {
        return elite2MockServer.findAll(getRequestedFor(urlEqualTo(url))).count()
    }

}
