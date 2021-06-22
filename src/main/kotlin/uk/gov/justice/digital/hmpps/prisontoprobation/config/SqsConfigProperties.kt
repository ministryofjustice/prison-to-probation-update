package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "hmpps.sqs")
data class SqsConfigProperties(
  val region: String,
  val provider: String,
  val localstackUrl: String = "",
  val queues: Map<String, QueueConfig>,
) {
  data class QueueConfig(
    val topicName: String = "",
    val queueName: String,
    val queueAccessKeyId: String = "",
    val queueSecretAccessKey: String = "",
    val dlqName: String,
    val dlqAccessKeyId: String = "",
    val dlqSecretAccessKey: String = "",
  )
}

fun SqsConfigProperties.dpsQueue() = queues["dpsQueue"] ?: throw MissingQueueException("dpsQueue has not been loaded from configuration properties")
fun SqsConfigProperties.hmppsQueue() = queues["hmppsQueue"] ?: throw MissingQueueException("hmppsQueue has not been loaded from configuration properties")
class MissingQueueException(message: String) : RuntimeException(message)
