package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
open class PrisonMovementService(
    private val offenderService: OffenderService,
    private val communityService: CommunityService,
    private val telemetryClient: TelemetryClient,
    @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun checkMovementAndUpdateProbation(prisonerMovementMessage: ExternalPrisonerMovementMessage) {
    val (bookingId, movementSeq) = prisonerMovementMessage
    val basicAttributes =  mapOf(
        "bookingId" to bookingId.toString(),
        "movementSeq" to movementSeq.toString())

    log.info("External movement for booking $bookingId with sequence $movementSeq")
    telemetryClient.trackEvent("P2PExternalMovement", basicAttributes, null)

    val movement = offenderService.getMovement(bookingId, movementSeq)
    val (name, attributes) = movement?.let {
      val movementAttributes = movementAttributes(bookingId, movement)
      movement.takeIf { isMovementTransferIntoPrison(movement) }?.let {
        movement.takeIf { isMovementToInterestedPrison(movement.toAgency) }?.let{
          offenderService.getBooking(bookingId).takeIf { it.activeFlag }?.let { booking ->
            offenderService.getOffender(movement.offenderNo).let { prisoner ->
              val updateAttributes = updateAttributes(movementAttributes, prisoner)
              communityService.updateProbationCustody(prisoner.offenderNo, booking.bookingNo, UpdateCustody(movement.toAgency!!))?.let {
                TelemetryEvent("P2PTransferProbationUpdated", updateAttributes + ("toAgencyDescription" to it.institution.description))
              } ?: TelemetryEvent("P2PTransferProbationRecordNotFound", updateAttributes)
            }
          } ?: TelemetryEvent("P2PTransferIgnored", movementAttributes + ("reason" to "Not an active booking"))
        } ?: TelemetryEvent("P2PTransferIgnored", movementAttributes + ("reason" to "Not an interested prison"))
      } ?: TelemetryEvent("P2PTransferIgnored", movementAttributes + ("reason" to "Not a transfer"))
    } ?: TelemetryEvent("P2PTransferIgnored", basicAttributes + ("reason" to "Not movement found"))

    telemetryClient.trackEvent(name, attributes, null)

  }


  private fun movementAttributes(bookingId: Long, movement: Movement) =
      mapOf("bookingId" to bookingId.toString(),
          "movementType" to movement.movementType,
          "fromAgency" to (movement.fromAgency ?: "not present"),
          "toAgency" to (movement.toAgency ?: "not present"))

  private fun updateAttributes(movementAttributes: Map<String, String>, prisoner: Prisoner) =
      movementAttributes + mapOf(
          "offenderNo" to prisoner.offenderNo,
          "firstName" to prisoner.firstName,
          "lastName" to prisoner.lastName,
          "latestLocation" to prisoner.latestLocation,
          "convictedStatus" to prisoner.convictedStatus
      )

  private fun isMovementTransferIntoPrison(movement: Movement) =
      movement.movementType == "ADM" && !movement.toAgency.isNullOrBlank()

  private fun isMovementToInterestedPrison(toAgency: String?) =
      allowAnyPrison() || allowedPrisons.contains(toAgency)

  private fun allowAnyPrison() = allowedPrisons.isEmpty()

  data class TelemetryEvent(val name: String, val attributes: Map<String, String?>)
}

