package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ReleaseAndRecallService(
  private val communityService: CommunityService,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun prisonerRecalled(nomsNumber: String, prisonId: String, recallDate: LocalDate) {

    val telemetryProperties = mapOf(
      "nomsNumber" to nomsNumber,
      "prisonId" to prisonId,
      "recallDate" to recallDate.format(DateTimeFormatter.ISO_DATE)
    )
    communityService.prisonerRecalled(nomsNumber, prisonId, recallDate)
      ?.let {
        telemetryClient.trackEvent(
          "P2PPrisonerRecalled",
          telemetryProperties,
          null
        )
      }
      ?: telemetryClient.trackEvent("P2PPrisonerNotRecalled", telemetryProperties, null)
  }

  fun prisonerReleased(nomsNumber: String, prisonId: String, releaseDate: LocalDate) {

    val telemetryProperties = mapOf(
      "nomsNumber" to nomsNumber,
      "prisonId" to prisonId,
      "releaseDate" to releaseDate.format(DateTimeFormatter.ISO_DATE)
    )
    communityService . prisonerReleased (nomsNumber, releaseDate)
    ?.let {
      telemetryClient.trackEvent(
        "P2PPrisonerReleased",
        mapOf(
          "nomsNumber" to nomsNumber,
          "releaseDate" to releaseDate.format(DateTimeFormatter.ISO_DATE)
        ),
        null
      )
    }
      ?: telemetryClient.trackEvent("P2PPrisonerNotReleased", mapOf("nomsNumber" to nomsNumber), null)
  }
}
