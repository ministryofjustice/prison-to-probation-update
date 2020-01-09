package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import org.springframework.stereotype.Service

@Service
open class OffenderService(@Qualifier("elite2ApiRestTemplate") private val restTemplate: OAuth2RestTemplate) {
  open fun getOffender(offenderId: String): Offender {
    val response = restTemplate.getForEntity("/elite2api/api/bookings/offenderNo/{offenderId}?fullInfo=true", Offender::class.java, offenderId)
    return response.body!!
  }
}

data class Offender(val offenderId: String) {
}
