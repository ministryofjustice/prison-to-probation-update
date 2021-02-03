package uk.gov.justice.digital.hmpps.prisontoprobation

import com.amazonaws.services.sqs.AmazonSQS
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisontoprobation.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageProcessor

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Value("\${token}")
  private val token: String? = null

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @SpyBean
  @Qualifier("awsSqsClient")
  internal lateinit var awsSqsClient: AmazonSQS

  @SpyBean
  internal lateinit var messageProcessor: MessageProcessor

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  protected lateinit var webTestClient: WebTestClient

  companion object {
    internal val prisonMockServer = PrisonMockServer()
    internal val oauthMockServer = OAuthMockServer()
    internal val communityMockServer = CommunityMockServer()
    internal val searchMockServer = SearchMockServer()

    @Suppress("unused")
    @BeforeAll
    @JvmStatic
    fun startMocks() {
      prisonMockServer.start()
      oauthMockServer.start()
      communityMockServer.start()
      searchMockServer.start()
    }

    @Suppress("unused")
    @AfterAll
    @JvmStatic
    fun stopMocks() {
      prisonMockServer.stop()
      oauthMockServer.stop()
      communityMockServer.stop()
      searchMockServer.stop()
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
    prisonMockServer.resetAll()
    communityMockServer.resetAll()
    searchMockServer.resetAll()

    oauthMockServer.stubGrantToken()
  }

  internal fun createHeaderEntity(entity: Any): HttpEntity<*> {
    val headers = HttpHeaders()
    headers.add("Authorization", "bearer $token")
    headers.contentType = MediaType.APPLICATION_JSON
    return HttpEntity(entity, headers)
  }

  internal fun Any.asJson() = objectMapper.writeValueAsBytes(this)

  internal fun setAuthorisation(
    user: String = "ptpu-report-client",
    roles: List<String> = listOf()
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles)
}
