package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@Service
open class CommunityService(@Qualifier("communityApiRestTemplate") private val restTemplate: RestTemplate) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun updateProbationCustody(offenderNo: String, bookingNo: String, updateCustody: UpdateCustody): Custody? {
    try {
      val response = restTemplate.exchange("/secure/offenders/nomsNumber/{nomsNumber}/custody/bookingNumber/{bookingNumber}", HttpMethod.PUT, HttpEntity(updateCustody), Custody::class.java, offenderNo, bookingNo)
      return response.body!!
    } catch (e : HttpClientErrorException) {
      if (e.statusCode != HttpStatus.NOT_FOUND) throw e
      log.info("Booking {} not found for {} message is {}", bookingNo, offenderNo, e.responseBodyAsString)
      return null
    }
  }
}

data class UpdateCustody (
        val nomsPrisonInstitutionCode: String
)

data class Institution(
        val description: String?
)

data class Custody (
        val institution: Institution
)