package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "hmpps.sqs")
data class SqsConfigProperties(
  val region: String,
  val provider: String,
  val sqsUrl: String = "",
  val snsUrl: String = "",
  val dpsQueue: QueueConfig,
  val hmppsQueue: QueueConfig,
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
