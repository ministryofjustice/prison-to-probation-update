package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import com.amazonaws.services.sqs.AmazonSQS
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.whereabouts.integration.wiremock.CommunityMockServer
import uk.gov.justice.digital.hmpps.whereabouts.integration.wiremock.Elite2MockServer
import uk.gov.justice.digital.hmpps.whereabouts.integration.wiremock.OAuthMockServer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Suppress("unused")
  @Autowired
  lateinit var restTemplate: TestRestTemplate

  @Value("\${token}")
  private val token: String? = null

  @SpyBean
  @Qualifier("awsSqsClient")
  internal lateinit var awsSqsClient: AmazonSQS

  companion object {
    internal val elite2MockServer = Elite2MockServer()
    internal val oauthMockServer = OAuthMockServer()
    internal val communityMockServer = CommunityMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      elite2MockServer.start()
      oauthMockServer.start()
      communityMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      elite2MockServer.stop()
      oauthMockServer.stop()
      communityMockServer.stop()
    }
  }

  init {
    SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("user", "pw")
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  @BeforeEach
  fun resetStubs() {
    oauthMockServer.resetAll()
    elite2MockServer.resetAll()
    communityMockServer.resetAll()

    oauthMockServer.stubGrantToken()
  }

  internal fun createHeaderEntity(entity: Any): HttpEntity<*> {
    val headers = HttpHeaders()
    headers.add("Authorization", "bearer $token")
    headers.contentType = MediaType.APPLICATION_JSON
    return HttpEntity(entity, headers)
  }
}
