package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ExternalMovementService(
  private val communityService: CommunityService,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val gson: Gson = GsonBuilder().create()
  }

  fun processPrisonerReceived(message: String) {
    val offenderMessage = gson.fromJson(message, OffenderMessage::class.java)
    // TODO (Reinstate call to community api when written)
    //  communityService.prisonerReceived(
    //  offenderMessage.additionalInformation.nomsNumber,offenderMessage.additionalInformation
    // )
    telemetryClient.trackEvent(
      "P2PPrisonerReceived",
      mapOf(
        "nomsNumber" to offenderMessage.additionalInformation.nomsNumber,
        "reason" to offenderMessage.additionalInformation.reason
      ),
      null
    )
  }
}

internal data class OffenderMessage(val version: String, val description: String, val additionalInformation: PrisonerReceivedDetails)
