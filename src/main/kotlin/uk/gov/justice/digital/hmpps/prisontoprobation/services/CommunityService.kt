package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
open class CommunityService(@Qualifier("communityApiRestTemplate") private val restTemplate: RestTemplate) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun updateProbationCustody(offender: Offender) {
  }
}

