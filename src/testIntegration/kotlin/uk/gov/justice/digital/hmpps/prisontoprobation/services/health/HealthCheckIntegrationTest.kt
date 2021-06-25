package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest

@ExtendWith(SpringExtension::class)
class HealthCheckIntegrationTest : NoQueueListenerIntegrationTest() {

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
  fun `Prison events queue health reports UP`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.prisonEventQueue-health.details.queueName").isEqualTo(queueName)
      .jsonPath("components.prisonEventQueue-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.prisonEventQueue-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.prisonEventQueue-health.details.dlqName").isEqualTo(dlqName)
      .jsonPath("components.prisonEventQueue-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.prisonEventQueue-health.details.messagesOnDlq").isEqualTo(0)
  }

  @Test
  fun `HMPPS domain events queue health reports UP`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.hmppsDomainEventQueue-health.details.queueName").isEqualTo(hmppsQueueName)
      .jsonPath("components.hmppsDomainEventQueue-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.hmppsDomainEventQueue-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.hmppsDomainEventQueue-health.details.dlqName").isEqualTo(hmppsDlqName)
      .jsonPath("components.hmppsDomainEventQueue-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.hmppsDomainEventQueue-health.details.messagesOnDlq").isEqualTo(0)
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
}
