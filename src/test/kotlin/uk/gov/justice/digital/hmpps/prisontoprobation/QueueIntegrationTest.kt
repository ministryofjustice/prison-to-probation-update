package uk.gov.justice.digital.hmpps.prisontoprobation

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.prisontoprobation.services.health.IntegrationTest

@ActiveProfiles(profiles = ["test", "test-queue"])
class QueueIntegrationTest : IntegrationTest() {

    @Autowired
    lateinit var queueUrl: String

    fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
        val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
        return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    }

    fun eliteRequestCountFor(url: String) = elite2MockServer.findAll(getRequestedFor(urlEqualTo(url))).count()

    fun communityPutCountFor(url: String)= communityMockServer.findAll(putRequestedFor(urlEqualTo(url))).count()

    fun communityPostCountFor(url: String)= communityMockServer.findAll(postRequestedFor(urlEqualTo(url))).count()
}
