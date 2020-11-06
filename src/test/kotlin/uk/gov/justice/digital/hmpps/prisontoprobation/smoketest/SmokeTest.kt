package uk.gov.justice.digital.hmpps.prisontoprobation.smoketest

import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Signal
import java.time.Duration

@SpringBootTest
@ActiveProfiles("test")
class SmokeTest {
  @Autowired
  private lateinit var smokeTestWebClient: WebClient

  @Test
  internal fun `When a imprisonment status event is raised probation will be updated`() {

    runBlocking {
      withTimeout(Duration.ofMinutes(10).toMillis()) {
        val results = waitForResults()
        assertThat(results.outcome).withFailMessage(results.description).isTrue
      }
    }
  }

  @Test
  internal fun `This will fail due to result failure`() {

    runBlocking {
      withTimeout(Duration.ofMinutes(10).toMillis()) {
        val results = waitForResults("FAIL")
        assertThat(results.outcome).withFailMessage(results.description).isTrue
      }
    }
  }

  @Test
  internal fun `This will fail due to timeout failure`() {

    runBlocking {
      withTimeout(Duration.ofMinutes(10).toMillis()) {
        val results = waitForResults("TIMEOUT")
        assertThat(results.outcome).withFailMessage(results.description).isTrue
      }
    }
  }

  suspend fun waitForResults(testMode: String = "SUCCEED"): TestResult = smokeTestWebClient.post()
      .uri("smoke-test?testMode=$testMode")
      .retrieve()
      .bodyToFlux(TestResult::class.java)
      .onErrorStop()
      .doOnEach(this::logUpdate)
      .awaitLast()

  private fun logUpdate(signal: Signal<TestResult>) {
    signal.let { it.get()?.let { result -> println(result.description) } }
  }

  data class TestResult(val description: String, val outcome: Boolean? = null)
}

