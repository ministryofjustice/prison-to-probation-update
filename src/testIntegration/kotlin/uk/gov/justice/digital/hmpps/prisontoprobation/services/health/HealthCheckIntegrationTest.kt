package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.ReflectionTestUtils
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest

@ExtendWith(SpringExtension::class)
class HealthCheckIntegrationTest : NoQueueListenerIntegrationTest() {
  @Autowired
  private lateinit var prisonEventsQueueHealth: PrisonEventsQueueHealth

  @Autowired
  private lateinit var hmppsEventsQueueHealth: HMPPSEventsQueueHealth

  @AfterEach
  fun tearDown() {
    ReflectionTestUtils.setField(prisonEventsQueueHealth, "queueName", queueName)
    ReflectionTestUtils.setField(prisonEventsQueueHealth, "dlqName", dlqName)
    ReflectionTestUtils.setField(hmppsEventsQueueHealth, "queueName", hmppsQueueName)
    ReflectionTestUtils.setField(hmppsEventsQueueHealth, "dlqName", hmppsDlqName)
  }

  @Test
  fun `Health page reports ok`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("OK")
      .jsonPath("components.prisonApiHealth.details.HttpStatus").isEqualTo("OK")
      .jsonPath("components.communityApiHealth.details.HttpStatus").isEqualTo("OK")
      .jsonPath("components.searchApiHealth.details.HttpStatus").isEqualTo("OK")
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health ping page is accessible`() {
    subPing(200)

    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health page reports down`() {
    subPing(404)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
      .jsonPath("components.prisonApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
      .jsonPath("components.communityApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
      .jsonPath("components.searchApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
  }

  @Test
  fun `Health page reports a teapot`() {
    subPing(418)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("components.prisonApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("components.communityApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("components.searchApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("status").isEqualTo("DOWN")
  }

  @Test
  fun `Queue does not exist reports down`() {
    ReflectionTestUtils.setField(prisonEventsQueueHealth, "queueName", "missing_queue")
    ReflectionTestUtils.setField(hmppsEventsQueueHealth, "queueName", "missing_queue")
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.prisonEventsQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("components.HMPPSEventsQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("status").isEqualTo("DOWN")
  }

  @Test
  fun `Queue health ok and dlq health ok, reports everything up`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("components.prisonEventsQueueHealth.status").isEqualTo("UP")
      .jsonPath("components.prisonEventsQueueHealth.status").isEqualTo(DlqStatus.UP.description)
      .jsonPath("components.HMPPSEventsQueueHealth.status").isEqualTo("UP")
      .jsonPath("components.HMPPSEventsQueueHealth.status").isEqualTo(DlqStatus.UP.description)
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Dlq health reports interesting attributes`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("components.prisonEventsQueueHealth.details.queueName").isEqualTo(queueName)
      .jsonPath("components.prisonEventsQueueHealth.details.dlqName").isEqualTo(dlqName)
      .jsonPath("components.prisonEventsQueueHealth.details.${QueueAttributes.MESSAGES_ON_DLQ.healthName}").isEqualTo(0)
      .jsonPath("components.HMPPSEventsQueueHealth.details.queueName").isEqualTo(hmppsQueueName)
      .jsonPath("components.HMPPSEventsQueueHealth.details.dlqName").isEqualTo(hmppsDlqName)
      .jsonPath("components.HMPPSEventsQueueHealth.details.${QueueAttributes.MESSAGES_ON_DLQ.healthName}").isEqualTo(0)
  }

  @Test
  fun `Dlq down brings main health and queue health down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.prisonEventsQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("components.prisonEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
      .jsonPath("components.HMPPSEventsQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("components.HMPPSEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `Dlq and queue down still shows queue names`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.prisonEventsQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("components.prisonEventsQueueHealth.details.queueName").isEqualTo(queueName)
      .jsonPath("components.prisonEventsQueueHealth.details.dlqName").isEqualTo(dlqName)
      .jsonPath("components.HMPPSEventsQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("components.HMPPSEventsQueueHealth.details.queueName").isEqualTo(hmppsQueueName)
      .jsonPath("components.HMPPSEventsQueueHealth.details.dlqName").isEqualTo(hmppsDlqName)
  }

  @Test
  fun `Main queue has no redrive policy reports dlq down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.prisonEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
      .jsonPath("components.HMPPSEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `Dlq not found reports dlq down`() {
    subPing(200)
    ReflectionTestUtils.setField(prisonEventsQueueHealth, "dlqName", "missing_queue")
    ReflectionTestUtils.setField(hmppsEventsQueueHealth, "dlqName", "missing_queue")

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.prisonEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)
      .jsonPath("components.HMPPSEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)
  }

  @Test
  fun `readiness reports ok`() {
    subPing(200)

    webTestClient.get()
      .uri("/health/readiness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    subPing(200)

    webTestClient.get()
      .uri("/health/liveness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  private fun subPing(status: Int) {
    oauthMockServer.stubFor(
      get("/auth/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    prisonMockServer.stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    communityMockServer.stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    searchMockServer.stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  private fun mockQueueWithoutRedrivePolicyAttributes() {
    val prisonEventsQueueName = ReflectionTestUtils.getField(prisonEventsQueueHealth, "queueName") as String
    val prisonEventsQueueUrl = awsSqsClient.getQueueUrl(prisonEventsQueueName)
    whenever(awsSqsClient.getQueueAttributes(GetQueueAttributesRequest(prisonEventsQueueUrl.queueUrl).withAttributeNames(listOf(QueueAttributeName.All.toString()))))
      .thenReturn(GetQueueAttributesResult())
    val hmppsEventsQueueName = ReflectionTestUtils.getField(hmppsEventsQueueHealth, "queueName") as String
    val hmppsEventsQueueUrl = hmppsAwsSqsClient.getQueueUrl(hmppsEventsQueueName)
    whenever(hmppsAwsSqsClient.getQueueAttributes(GetQueueAttributesRequest(hmppsEventsQueueUrl.queueUrl).withAttributeNames(listOf(QueueAttributeName.All.toString()))))
      .thenReturn(GetQueueAttributesResult())
  }
}
