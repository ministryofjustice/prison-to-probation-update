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

  fun prisonerRecalled(nomsNumber: String, occurred: LocalDate) =
    communityService.prisonerRecalled(nomsNumber, occurred)
      ?.let {
        telemetryClient.trackEvent(
          "P2PPrisonerRecalled",
          mapOf(
            "nomsNumber" to nomsNumber,
            "occurred" to occurred.format(DateTimeFormatter.ISO_DATE)
          ),
          null
        )
      }
      ?: telemetryClient.trackEvent("P2PPrisonerNotRecalled", mapOf("nomsNumber" to nomsNumber), null)

  fun prisonerReleased(nomsNumber: String, occurred: LocalDate) =
    communityService.prisonerReleased(nomsNumber, occurred)
      ?.let {
        telemetryClient.trackEvent(
          "P2PPrisonerReleased",
          mapOf(
            "nomsNumber" to nomsNumber,
            "occurred" to occurred.format(DateTimeFormatter.ISO_DATE)
          ),
          null
        )
      }
      ?: telemetryClient.trackEvent("P2PPrisonerNotReleased", mapOf("nomsNumber" to nomsNumber), null)
}
