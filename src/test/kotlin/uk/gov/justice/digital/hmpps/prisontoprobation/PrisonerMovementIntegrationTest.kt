package uk.gov.justice.digital.hmpps.prisontoprobation

import com.amazonaws.services.sqs.AmazonSQS
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-message-test", "test")
@RunWith(SpringJUnit4ClassRunner::class)
class PrisonerMovementIntegrationTest {

    @Qualifier("awsLocalTestClient")
    @Autowired
    private val sqsClient: AmazonSQS? = null

    @Autowired
    private val queueUrl: String? = null

    @Test
    fun `will consume a prison movement message`() {
        val message = this::class.java.getResource("/messages/externalMovement.json").readText()

        sqsClient!!.sendMessage(queueUrl, message)

        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { count -> count == 0 }
    }

    private fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
        val queueAttributes = sqsClient!!.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
        return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    }

}
