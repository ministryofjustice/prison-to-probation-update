package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test-queue")
open class TestQueueConfig(
                           private val sqsClient: AmazonSQS,
                           @Value("\${sqs.queue.name}")
                           private val queueName: String) {
    @Bean
    open fun queueUrl(): String {
        sqsClient.createQueue(queueName)
        val queueUrl = sqsClient.getQueueUrl(queueName).queueUrl
        sqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
        return queueUrl
    }

}
