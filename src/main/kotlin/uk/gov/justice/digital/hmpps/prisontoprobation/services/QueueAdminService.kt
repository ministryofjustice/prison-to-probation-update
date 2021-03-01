package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class QueueAdminService(
  private val awsSqsClient: AmazonSQS,
  private val awsSqsDlqClient: AmazonSQS,
  private val telemetryClient: TelemetryClient,
  @Value("\${sqs.queue.name}") private val eventQueueName: String,
  @Value("\${sqs.dlq.name}") private val eventDlqName: String,
  private val gson: Gson
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val eventQueueUrl: String by lazy { awsSqsClient.getQueueUrl(eventQueueName).queueUrl }
  val eventDlqUrl: String by lazy { awsSqsDlqClient.getQueueUrl(eventDlqName).queueUrl }

  fun clearAllDlqMessagesForEvent() {
    awsSqsDlqClient.purgeQueue(PurgeQueueRequest(eventDlqUrl))
    log.info("Clear all messages on event dead letter queue")
  }

  fun transferEventMessages() =
    repeat(getEventDlqMessageCount()) {
      awsSqsDlqClient.receiveMessage(ReceiveMessageRequest(eventDlqUrl).withMaxNumberOfMessages(1)).messages
        .forEach { msg ->
          awsSqsClient.sendMessage(eventQueueUrl, msg.body)
          awsSqsDlqClient.deleteMessage(DeleteMessageRequest(eventDlqUrl, msg.receiptHandle))
        }
    }

  fun getEventDlqMessageCount() =
    awsSqsDlqClient.getQueueAttributes(eventDlqUrl, listOf("ApproximateNumberOfMessages"))
      .attributes["ApproximateNumberOfMessages"]
      ?.toInt() ?: 0
}
