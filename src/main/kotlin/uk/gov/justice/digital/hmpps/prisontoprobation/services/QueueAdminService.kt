package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.config.SqsConfigProperties
import uk.gov.justice.digital.hmpps.prisontoprobation.config.TelemetryEvents

@Service
class QueueAdminService(
  private val awsSqsClient: AmazonSQS,
  private val awsSqsDlqClient: AmazonSQS,
  private val telemetryClient: TelemetryClient,
  private val sqsConfigProperties: SqsConfigProperties,
  private val gson: Gson
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val eventQueueName: String by lazy { sqsConfigProperties.dpsQueue.queueName }
  val eventDlqName: String by lazy { sqsConfigProperties.dpsQueue.dlqName }
  val eventQueueUrl: String by lazy { awsSqsClient.getQueueUrl(eventQueueName).queueUrl }
  val eventDlqUrl: String by lazy { awsSqsDlqClient.getQueueUrl(eventDlqName).queueUrl }

  fun clearAllDlqMessagesForEvent() {
    getEventDlqMessageCount()
      .takeIf { it > 0 }
      ?.also { total ->
        awsSqsDlqClient.purgeQueue(PurgeQueueRequest(eventDlqUrl))
        log.info("Clear all messages on event dead letter queue")
        telemetryClient.trackEvent(
          TelemetryEvents.PURGED_EVENT_DLQ.name, mapOf("messages-on-queue" to "$total"), null
        )
      }
  }

  fun transferEventMessages() {
    getEventDlqMessageCount()
      .takeIf { it > 0 }
      ?.also { total ->
        repeat(total) {
          awsSqsDlqClient.receiveMessage(ReceiveMessageRequest(eventDlqUrl).withMaxNumberOfMessages(1)).messages
            .forEach { msg ->
              awsSqsClient.sendMessage(eventQueueUrl, msg.body)
              awsSqsDlqClient.deleteMessage(DeleteMessageRequest(eventDlqUrl, msg.receiptHandle))
            }
        }
      }?.also { total ->
        telemetryClient.trackEvent(
          TelemetryEvents.TRANSFERRED_EVENT_DLQ.name, mapOf("messages-on-queue" to "$total"), null
        )
      }
  }

  fun getEventDlqMessageCount() =
    awsSqsDlqClient.getQueueAttributes(eventDlqUrl, listOf("ApproximateNumberOfMessages"))
      .attributes["ApproximateNumberOfMessages"]
      ?.toInt() ?: 0
}
