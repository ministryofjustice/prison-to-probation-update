package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReleaseAndRecallService(
  private val communityService: CommunityService,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun prisonerRecalled(offenderMessage: OffenderMessage) =
    communityService.prisonerRecalled(offenderMessage.additionalInformation.nomsNumber, offenderMessage.additionalInformation)
      ?.let {
        telemetryClient.trackEvent(
          "P2PPrisonerRecalled",
          mapOf(
            "nomsNumber" to offenderMessage.additionalInformation.nomsNumber,
            "details" to offenderMessage.additionalInformation.details
          ),
          null
        )
      }
      ?: telemetryClient.trackEvent(("P2PPrisonerNotRecalled"))
}
