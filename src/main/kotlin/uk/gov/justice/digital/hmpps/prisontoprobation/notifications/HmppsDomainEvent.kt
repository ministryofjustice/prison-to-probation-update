package uk.gov.justice.digital.hmpps.prisontoprobation.notifications

import java.time.ZonedDateTime

data class HmppsDomainEvent(
  val eventType: String,
  val version: Int = 1,
  val detailUrl: String? = null,
  val occurredAt: ZonedDateTime = ZonedDateTime.now(),
  val description: String? = null,
  val additionalInformation: Map<String, Any?> = mapOf(),
  val personReference: PersonReference = PersonReference(),
)

data class PersonReference(val identifiers: List<PersonIdentifier> = listOf())

data class PersonIdentifier(val type: String, val value: String)
