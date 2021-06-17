package uk.gov.justice.digital.hmpps.prisontoprobation

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisontoprobation.config.SqsConfigProperties
import uk.gov.justice.digital.hmpps.prisontoprobation.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisontoprobation.repositories.MessageRepository
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageProcessor
import uk.gov.justice.digital.hmpps.prisontoprobation.services.PrisonerChangesListenerPusher
import uk.gov.justice.digital.hmpps.prisontoprobation.services.QueueAdminService

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Value("\${token}")
  private val token: String? = null

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  protected lateinit var sqsConfigProperties: SqsConfigProperties

  @SpyBean
  @Qualifier("awsSqsClient")
  protected lateinit var awsSqsClient: AmazonSQS

  internal val queueName: String by lazy { sqsConfigProperties.dpsQueue.queueName }

  @SpyBean
  @Qualifier("hmppsAwsSqsClient")
  protected lateinit var hmppsAwsSqsClient: AmazonSQS

  internal val hmppsQueueName: String by lazy { sqsConfigProperties.hmppsQueue.queueName }

  @SpyBean
  @Qualifier("awsSqsDlqClient")
  internal lateinit var awsSqsDlqClient: AmazonSQS

  @SpyBean
  @Qualifier("hmppsAwsSqsDlqClient")
  internal lateinit var hmppsAwsSqsDlqClient: AmazonSQS

  internal val dlqName: String by lazy { sqsConfigProperties.dpsQueue.dlqName }

  internal val hmppsDlqName: String by lazy { sqsConfigProperties.hmppsQueue.dlqName }

  @SpyBean
  internal lateinit var messageProcessor: MessageProcessor

  @SpyBean
  internal lateinit var queueAdminService: QueueAdminService

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

  val queueUrl: String by lazy { awsSqsClient.getQueueUrl(queueName).queueUrl }
  val dlqUrl: String by lazy { awsSqsDlqClient.getQueueUrl(dlqName).queueUrl }

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
    awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    awsSqsDlqClient.purgeQueue(PurgeQueueRequest(dlqUrl))
    await untilCallTo { awsSqsClient.activeMessageCount(queueUrl) } matches { it == 0 }
    await untilCallTo { awsSqsDlqClient.activeMessageCount(dlqUrl) } matches { it == 0 }
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
}
