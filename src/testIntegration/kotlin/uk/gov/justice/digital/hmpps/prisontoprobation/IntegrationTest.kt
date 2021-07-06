package uk.gov.justice.digital.hmpps.prisontoprobation

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisontoprobation.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageProcessor
import uk.gov.justice.digital.hmpps.prisontoprobation.services.PrisonerChangesListenerPusher
import uk.gov.justice.hmpps.sqs.HmppsQueueFactory
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.MissingQueueException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTest.SqsConfig::class)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Value("\${token}")
  private val token: String? = null

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  fun HmppsSqsProperties.prisonEventQueueConfig() =
    queues["prisoneventqueue"] ?: throw MissingQueueException("prisoneventqueue has not been loaded from configuration properties")

  fun HmppsSqsProperties.hmppsEventQueueConfig() =
    queues["hmppseventqueue"] ?: throw MissingQueueException("hmppseventqueue has not been loaded from configuration properties")

  protected val prisonEventQueue by lazy { hmppsQueueService.findByQueueId("prisoneventqueue") ?: throw MissingQueueException("HmppsQueue prisoneventqueue not found") }
  protected val hmppsEventQueue by lazy { hmppsQueueService.findByQueueId("hmppseventqueue") ?: throw MissingQueueException("HmppsQueue hmppseventqueue not found") }

  @Autowired
  protected lateinit var hmppsSqsProperties: HmppsSqsProperties

  @SpyBean
  @Qualifier("prisoneventqueue-sqs-client")
  protected lateinit var prisonEventQueueSqsClient: AmazonSQS

  internal val queueName: String by lazy { prisonEventQueue.queueName }

  @SpyBean
  @Qualifier("hmppseventqueue-sqs-client")
  protected lateinit var hmppsEventQueueSqsClient: AmazonSQS

  internal val hmppsQueueName: String by lazy { hmppsEventQueue.queueName }

  @SpyBean
  @Qualifier("prisoneventqueue-sqs-dlq-client")
  internal lateinit var prisonEventSqsDlqClient: AmazonSQS

  @SpyBean
  @Qualifier("hmppseventqueue-sqs-dlq-client")
  internal lateinit var hmppsEventSqsDlqClient: AmazonSQS

  internal val dlqName: String by lazy { prisonEventQueue.dlqName }

  internal val hmppsDlqName: String by lazy { hmppsEventQueue.dlqName }

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

  val queueUrl: String by lazy { prisonEventQueueSqsClient.getQueueUrl(queueName).queueUrl }
  val dlqUrl: String by lazy { prisonEventSqsDlqClient.getQueueUrl(dlqName).queueUrl }
  val hmppsQueueUrl: String by lazy { hmppsEventQueueSqsClient.getQueueUrl(hmppsQueueName).queueUrl }

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
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    hmppsEventQueueSqsClient.purgeQueue(PurgeQueueRequest(hmppsQueueUrl))
    await untilCallTo { prisonEventQueueSqsClient.activeMessageCount(queueUrl) } matches { it == 0 }
    await untilCallTo { prisonEventSqsDlqClient.activeMessageCount(dlqUrl) } matches { it == 0 }
    await untilCallTo { hmppsEventQueueSqsClient.activeMessageCount(hmppsQueueUrl) } matches { it == 0 }
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

  internal fun AmazonSQS.activeMessageCount(queueUrl: String): Int {
    val queueAttributes = this.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"))
    val msgsOnQueue = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0
    val msgsInFlight = queueAttributes.attributes["ApproximateNumberOfMessagesNotVisible"]?.toInt() ?: 0
    return msgsOnQueue + msgsInFlight
  }

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

  @TestConfiguration
  class SqsConfig(private val hmppsQueueFactory: HmppsQueueFactory, private val hmppsSqsProperties: HmppsSqsProperties) {

    val prisonEventConfig = hmppsSqsProperties.queues["prisoneventqueue"]
      ?: throw MissingQueueException("HmppsSqsProperties config for prisoneventqueue not found")
    val hmppsEventConfig = hmppsSqsProperties.queues["hmppseventqueue"]
      ?: throw MissingQueueException("HmppsSqsProperties config for hmppseventqueue not found")

    @Bean("prisoneventqueue-sqs-dlq-client")
    fun prisonEventQueueSqsDlqClient(): AmazonSQS =
      hmppsQueueFactory.createSqsDlqClient(prisonEventConfig, hmppsSqsProperties)

    @Bean("prisoneventqueue-sqs-client")
    fun prisonEventQueueSqsClient(
      hmppsSqsProperties: HmppsSqsProperties,
      @Qualifier("prisoneventqueue-sqs-dlq-client") prisonEventQueueSqsDlqClient: AmazonSQS
    ): AmazonSQS =
      hmppsQueueFactory.createSqsClient(prisonEventConfig, hmppsSqsProperties, prisonEventQueueSqsDlqClient)

    @Bean("hmppseventqueue-sqs-dlq-client")
    fun hmppsEventQueueSqsDlqClient(): AmazonSQS =
      hmppsQueueFactory.createSqsDlqClient(hmppsEventConfig, hmppsSqsProperties)

    @Bean("hmppseventqueue-sqs-client")
    fun hmppsEventQueueSqsClient(
      hmppsSqsProperties: HmppsSqsProperties,
      @Qualifier("hmppseventqueue-sqs-dlq-client") hmppsEventQueueSqsDlqClient: AmazonSQS
    ): AmazonSQS =
      hmppsQueueFactory.createSqsClient(hmppsEventConfig, hmppsSqsProperties, hmppsEventQueueSqsDlqClient)
  }
}
