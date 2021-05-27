package uk.gov.justice.digital.hmpps.prisontoprobation.services.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "embedded-localstack")
class LocalStackConfig {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  fun localStackContainer(): LocalStackContainer {
    log.info("Starting localstack...")
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")
    val localStackContainer: LocalStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack").withTag("0.11.2"))
      .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS, LocalStackContainer.Service.DYNAMODB)
      .withClasspathResourceMapping("/localstack/setup-sns.sh", "/docker-entrypoint-initaws.d/setup-sns.sh", BindMode.READ_WRITE)
      .withEnv("HOSTNAME_EXTERNAL", "localhost")
      .withEnv("DEFAULT_REGION", "eu-west-2")
      .waitingFor(
        Wait.forLogMessage(".*All Ready.*", 1)
      )

    log.info("Started localstack.")

    localStackContainer.start()
    localStackContainer.followOutput(logConsumer)
    return localStackContainer
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun queueUrl(
    @Autowired awsSqsClient: AmazonSQS,
    @Value("\${sqs.queue.name}") queueName: String,
    @Value("\${sqs.dlq.name}") dlqName: String
  ): String {
    val result = awsSqsClient.createQueue(CreateQueueRequest(dlqName))
    val dlqArn = awsSqsClient.getQueueAttributes(result.queueUrl, listOf(QueueAttributeName.QueueArn.toString()))
    awsSqsClient.createQueue(
      CreateQueueRequest(queueName).withAttributes(
        mapOf(
          QueueAttributeName.RedrivePolicy.toString() to
            """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"5"}"""
        )
      )
    )
    return awsSqsClient.getQueueUrl(queueName).queueUrl
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun hmppsQueueUrl(
    @Autowired hmppsAwsSqsClient: AmazonSQS,
    @Value("\${sqs.hmpps.queue.name}") queueName: String,
    @Value("\${sqs.hmpps.dlq.name}") dlqName: String
  ): String {
    val result = hmppsAwsSqsClient.createQueue(CreateQueueRequest(dlqName))
    val dlqArn = hmppsAwsSqsClient.getQueueAttributes(result.queueUrl, listOf(QueueAttributeName.QueueArn.toString()))
    hmppsAwsSqsClient.createQueue(
      CreateQueueRequest(queueName).withAttributes(
        mapOf(
          QueueAttributeName.RedrivePolicy.toString() to
            """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"5"}"""
        )
      )
    )
    return hmppsAwsSqsClient.getQueueUrl(queueName).queueUrl
  }
}
