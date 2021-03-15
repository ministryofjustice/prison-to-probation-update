package uk.gov.justice.digital.hmpps.prisontoprobation.resource

import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
        "/queue-admin/purge-event-dlq",
        "/queue-admin/transfer-event-dlq",
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

    @Test
    internal fun `purge - satisfies the correct role`() {
      doNothing().whenever(queueAdminService).clearAllDlqMessagesForEvent()

      webTestClient.put()
        .uri("/queue-admin/purge-event-dlq")
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_QUEUE_ADMIN")))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

      verify(queueAdminService).clearAllDlqMessagesForEvent()
    }

    @Test
    internal fun `transfer - satisfies the correct role`() {
      doNothing().whenever(queueAdminService).transferEventMessages()

      webTestClient.put()
        .uri("/queue-admin/transfer-event-dlq")
        .headers(setAuthorisation(roles = listOf("ROLE_PTPU_QUEUE_ADMIN")))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

      verify(queueAdminService).transferEventMessages()
    }
  }
}
