package uk.gov.justice.digital.hmpps.prisontoprobation.e2e

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.springframework.boot.test.context.SpringBootTest
import uk.gov.justice.digital.hmpps.prisontoprobation.IntegrationTest

@SpringBootTest(
  properties = [
    "prisontoprobation.message-processor.enabled=true",
    "prisontoprobation.message-processor.delay=50",
    "prisontoprobation.hold-back.duration=0m"
  ],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class QueueListenerIntegrationTest : IntegrationTest() {

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun getNumberOfMessagesCurrentlyOnDlq(): Int? {
    val queueAttributes = awsSqsDlqClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun getNumberOfMessagesCurrentlyOnHmppsQueue(): Int? {
    val queueAttributes = hmppsAwsSqsClient.getQueueAttributes(hmppsQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun eliteRequestCountFor(url: String) = prisonMockServer.findAll(getRequestedFor(urlEqualTo(url))).count()

  fun communityPutCountFor(url: String) = communityMockServer.findAll(putRequestedFor(urlEqualTo(url))).count()

  fun communityPostCountFor(url: String) = communityMockServer.findAll(postRequestedFor(urlEqualTo(url))).count()

  fun communityGetCountFor(url: String) = communityMockServer.findAll(getRequestedFor(urlEqualTo(url))).count()

  fun offenderSearchPostCountFor(url: String) = searchMockServer.findAll(postRequestedFor(urlEqualTo(url))).count()
}
