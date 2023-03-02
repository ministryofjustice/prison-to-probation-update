package uk.gov.justice.digital.hmpps.prisontoprobation

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.prisontoprobation.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageProcessor
import uk.gov.justice.digital.hmpps.prisontoprobation.services.PrisonerChangesListenerPusher
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Value("\${token}")
  private val token: String? = null

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  fun HmppsSqsProperties.prisonEventQueueConfig() =
    queues["prisoneventqueue"] ?: throw MissingQueueException("prisoneventqueue has not been loaded from configuration properties")

  protected val prisonEventQueue by lazy { hmppsQueueService.findByQueueId("prisoneventqueue") ?: throw MissingQueueException("HmppsQueue prisoneventqueue not found") }

  @Autowired
  protected lateinit var hmppsSqsProperties: HmppsSqsProperties

  protected val prisonEventQueueSqsClient by lazy { prisonEventQueue.sqsClient }
  protected val prisonEventSqsDlqClient by lazy { prisonEventQueue.sqsDlqClient }

  internal val prisonEventQueueName by lazy { prisonEventQueue.queueName }
  internal val prisonEventDlqName by lazy { prisonEventQueue.dlqName as String }

  @SpyBean
  internal lateinit var messageProcessor: MessageProcessor

  @SpyBean
  internal lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var applicationContext: ApplicationContext

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired(required = false)
  private lateinit var prisonerChangesListenerPusher: PrisonerChangesListenerPusher

  @Autowired
  protected lateinit var messageRepository: MessageRepository

  val queueUrl: String by lazy { prisonEventQueue.queueUrl }
  val dlqUrl: String by lazy { prisonEventQueue.dlqUrl!! }

  @BeforeEach
  fun `Debug bean information`() {
    log.info("Starting integration test applicationContext=${applicationContext.hashCode()}")
    try {
      log.info("...with prisonerChangesListenerPusher=${prisonerChangesListenerPusher.hashCode()}")
    } catch (e: Exception) {
      log.info("...with prisonerChangesListenerPusher=null")
    }
  }

  @BeforeEach
  fun `Reset resources`() {
    messageRepository.deleteAll()
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build())
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build())
    await untilCallTo { prisonEventQueueSqsClient.countMessagesOnQueue(queueUrl).get() } matches { it == 0 }
    await untilCallTo { prisonEventSqsDlqClient!!.countMessagesOnQueue(dlqUrl).get() } matches { it == 0 }
  }

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
    val prisonMockServer = PrisonMockServer()
    internal val oauthMockServer = OAuthMockServer()
    val communityMockServer = CommunityMockServer()
    val searchMockServer = SearchMockServer()

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

  protected fun setAuthorisation(
    user: String = "ptpu-report-client",
    roles: List<String> = listOf()
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles)
  protected fun subPing(status: Int) {
    oauthMockServer.stubFor(
      WireMock.get("/auth/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    prisonMockServer.stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    communityMockServer.stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    searchMockServer.stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }
}
