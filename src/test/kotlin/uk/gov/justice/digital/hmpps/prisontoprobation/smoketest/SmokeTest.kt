package uk.gov.justice.digital.hmpps.prisontoprobation.smoketest

import junit.framework.Assert.fail
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Signal
import java.time.Duration

@SpringBootTest
@ActiveProfiles("test")
class SmokeTest {

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @Autowired
  private lateinit var smokeTestWebClient: WebClient

  @Test
  internal fun `The smoke test succeeds`() {

    runBlocking {
      try {
        withTimeout(Duration.ofMinutes(9).toMillis()) {
          val results = waitForResults()
          assertThat(results.outcome)
              .withFailMessage("Expected outcome=true but outcome=${results.outcome}  (test results description=${results.description})")
              .isTrue
        }
      } catch (e: Exception) {
        logException(e)
        fail("Received exception ${e.message}, but was expecting smoke test to succeed with outcome=true")
      }
    }
  }

  fun logException(e: Exception) {
    when (e) {
      is TimeoutCancellationException -> log.error("Timed out waiting for test results", e)
      else -> log.error("Unexpected exception ", e)
    }

  }

  @Test
  internal fun `The smoke test fails`() {

    runBlocking {
      try {
        withTimeout(Duration.ofMinutes(9).toMillis()) {
          val results = waitForResults("FAIL")
          assertThat(results.outcome)
              .withFailMessage("Expected outcome=false but outcome=${results.outcome}  (test results description=${results.description})")
              .isFalse
        }
      } catch (e: Exception) {
        logException(e)
        fail("Received exception ${e.message}, but was expecting smoke test to fail with outcome=fail")
      }
    }
  }

  @Test
  internal fun `The test times out on the client side`() {

    runBlocking {
      try {
        withTimeout(Duration.ofMinutes(5).toMillis()) {
          val results = waitForResults("TIMEOUT")
          fail("Not expecting to receive result ${results}, the test should have timed out on the client side")
        }
      } catch (e: Exception) {
        logException(e)
        assertThat(e)
            .withFailMessage("Expecting test to timeout on the client side with a TimeoutCancellationException")
            .isInstanceOf(TimeoutCancellationException::class.java)
      }
    }
  }

  @Test
  internal fun `The test never receives an outcome from dps-smoketest`() {

    runBlocking {
      try {
        withTimeout(Duration.ofSeconds((9*60)+30).toMillis()) {
          val results = waitForResults("TIMEOUT")
          assertThat(results.outcome)
              .withFailMessage("Expected outcome=null but outcome=${results.outcome}  (test results description=${results.description})")
              .isNull()
        }
      } catch (e: Exception) {
        logException(e)
        fail("Received exception ${e.message}, but was expecting no test result with outcome=null")
      }
    }
  }

  suspend fun waitForResults(testMode: String = "SUCCEED"): TestResult = smokeTestWebClient.post()
      .uri("smoke-test?testMode=$testMode")
      .retrieve()
      .bodyToFlux(TestResult::class.java)
      .doOnError { log.error("Received error while waiting for results", it) }
      .onErrorStop()
      .doOnEach(this::logUpdate)
      .awaitLast()

  private fun logUpdate(signal: Signal<TestResult>) {
    signal.let { it.get()?.let { result -> println(result.description) } }
  }

  data class TestResult(val description: String, val outcome: Boolean? = null)
}

