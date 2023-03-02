package uk.gov.justice.digital.hmpps.prisontoprobation.resource

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisontoprobation.NoQueueListenerIntegrationTest

class QueueResourceTest : NoQueueListenerIntegrationTest() {

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class SecureEndpoints {
    private fun secureEndpoints() =
      listOf(
        "/queue-admin/purge-queue/any",
        "/queue-admin/retry-dlq/any",
      )

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires a valid authentication token`(uri: String) {
      webTestClient.put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
    }

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires the correct role`(uri: String) {
      webTestClient.put()
        .uri(uri)
        .headers(setAuthorisation(roles = listOf()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isForbidden
    }
  }
}
