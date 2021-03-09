package uk.gov.justice.digital.hmpps.prisontoprobation

import com.amazonaws.services.sqs.AmazonSQS
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisontoprobation.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageProcessor
import uk.gov.justice.digital.hmpps.prisontoprobation.services.PrisonerChangesListenerPusher

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

  @Autowired
  private lateinit var applicationContext: ApplicationContext

  @Autowired(required = false)
  private lateinit var jmsListenerContainerFactory: DefaultJmsListenerContainerFactory

  @Autowired(required = false)
  lateinit var webTestClient: WebTestClient

  @Autowired(required = false)
  private lateinit var prisonerChangesListenerPusher: PrisonerChangesListenerPusher

  @BeforeEach
  fun `check context and some beans`() {
    log.info(">>>>>>>>>> applicationContext=${applicationContext.hashCode()}")
    log.info(">>>>>>>>>> jmsListenerContainerFactory=${jmsListenerContainerFactory.hashCode()}")
    log.info(">>>>>>>>>> webTestClient=${webTestClient.hashCode()}")
    try {
      log.info(">>>>>>>>>> prisonerChangesListenerPusher=${prisonerChangesListenerPusher.hashCode()}")
    } catch (e: Exception) {
      log.info(">>>>>>>>>> prisonerChangesListenerPusher=null")
    }
  }

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
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
