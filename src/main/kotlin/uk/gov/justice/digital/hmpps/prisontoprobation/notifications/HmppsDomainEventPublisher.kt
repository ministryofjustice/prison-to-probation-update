package uk.gov.justice.digital.hmpps.prisontoprobation.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingTopicException

@Service
class HmppsDomainEventPublisher(
  hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val domainEventsTopic = hmppsQueueService.findByTopicId("domaineventtopic") ?: throw MissingTopicException("Missing topic details for HMPPS Domain Events")

  fun publish(event: HmppsDomainEvent) {
    domainEventsTopic.snsClient.publish(
      PublishRequest.builder()
        .topicArn(domainEventsTopic.arn)
        .message(objectMapper.writeValueAsString(event))
        .messageAttributes(mapOf("eventType" to MessageAttributeValue.builder().dataType("String").stringValue(event.eventType).build()))
        .build(),
    )
  }
}
