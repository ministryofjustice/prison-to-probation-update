package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.PrisonMovementService.Companion.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.PrisonMovementService.Companion.Result.Success

@Service
open class PrisonMovementService(
    private val offenderService: OffenderService,
    private val communityService: CommunityService,
    private val telemetryClient: TelemetryClient,
    @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    sealed class Result<out T, out E> {
      data class Success<out T>(val value: T) : Result<T, Nothing>()
      data class Ignore<out E>(val reason: E) : Result<Nothing, E>()
    }

    data class TelemetryEvent(val name: String, val attributes: Map<String, String?>)
  }

  open fun checkMovementAndUpdateProbation(prisonerMovementMessage: ExternalPrisonerMovementMessage) {
    val (bookingId, movementSeq) = prisonerMovementMessage
    val trackingAttributes = mapOf(
        "bookingId" to bookingId.toString(),
        "movementSeq" to movementSeq.toString())

    log.info("External movement for booking $bookingId with sequence $movementSeq")
    telemetryClient.trackEvent("P2PExternalMovement", trackingAttributes, null)

    val (name, attributes) = processMovement(bookingId, movementSeq, trackingAttributes)

    telemetryClient.trackEvent(name, attributes, null)

  }

  private fun processMovement(bookingId: Long, movementSeq: Long, trackingAttributes: Map<String, String>): TelemetryEvent {
    var movement = movementOf(bookingId, movementSeq, trackingAttributes).onIgnore { return it.reason}

    val movementTrackingAttributes = movementTrackingAttributesFor(bookingId, movement)

    val toAgency = toAgencyForPrisonTransfer(movement, movementTrackingAttributes).onIgnore { return it.reason }
    movement = interestedPrisonMovement(movement, movementTrackingAttributes).onIgnore { return it.reason }
    val booking = activeBooking(bookingId, movementTrackingAttributes).onIgnore { return it.reason }
    val prisoner = offenderService.getOffender(movement.offenderNo)

    val updateTrackingAttributes = updateTrackingAttributesFor(movementTrackingAttributes, prisoner)

    return communityService.updateProbationCustody(prisoner.offenderNo, booking.bookingNo, UpdateCustody(toAgency))?.let {
      TelemetryEvent("P2PTransferProbationUpdated", updateTrackingAttributes + ("toAgencyDescription" to it.institution.description))
    } ?: TelemetryEvent("P2PTransferProbationRecordNotFound", updateTrackingAttributes)
  }

  private fun toAgencyForPrisonTransfer(movement: Movement, trackingAttributes: Map<String, String>): Result<String, TelemetryEvent> {
    return movement.toAgency?.takeIf { isMovementTransferIntoPrison(movement) }
        ?.let { Success(it) }
        ?:Ignore(TelemetryEvent("P2PTransferIgnored", trackingAttributes + ("reason" to "Not a transfer")))
  }


  private fun interestedPrisonMovement(movement: Movement, trackingAttributes: Map<String, String>): Result<Movement, TelemetryEvent> =
      if (isMovementToInterestedPrison(movement.toAgency)) {
        Success(movement)
      } else {
        Ignore(TelemetryEvent("P2PTransferIgnored", trackingAttributes + ("reason" to "Not an interested prison")))
      }

  private fun movementOf(bookingId: Long, movementSeq: Long, trackingAttributes: Map<String, String>): Result<Movement, TelemetryEvent> {
    val movement = offenderService.getMovement(bookingId, movementSeq)

    return movement?.let { Success(movement) }
        ?: Ignore(TelemetryEvent("P2PTransferIgnored", trackingAttributes + ("reason" to "Movement not found")))
  }

  private fun activeBooking(bookingId: Long, trackingAttributes: Map<String, String>): Result<Booking, TelemetryEvent> =
      offenderService.getBooking(bookingId).takeIf { it.activeFlag }?.let { Success(it) }
          ?: Ignore(TelemetryEvent("P2PTransferIgnored", trackingAttributes + ("reason" to "Not an active booking")))

  private fun isMovementToInterestedPrison(toAgency: String?) =
      allowAnyPrison() || allowedPrisons.contains(toAgency)

  private fun allowAnyPrison() = allowedPrisons.isEmpty()

  // either return the value or execute the block that must return "nothing" e.g it must throw or return out of parent
  private inline fun <T, E> Result<T, E>.onIgnore(block: (Ignore<E>) -> Nothing): T {
    return when (this) {
      is Success<T> -> value
      is Ignore<E> -> block(this)
    }
  }
}

private fun isMovementTransferIntoPrison(movement: Movement) =
    movement.movementType == "ADM"

private fun updateTrackingAttributesFor(movementAttributes: Map<String, String>, prisoner: Prisoner) =
    movementAttributes + mapOf(
        "offenderNo" to prisoner.offenderNo,
        "firstName" to prisoner.firstName,
        "lastName" to prisoner.lastName,
        "latestLocation" to prisoner.latestLocation,
        "convictedStatus" to prisoner.convictedStatus
    )

private fun movementTrackingAttributesFor(bookingId: Long, movement: Movement) =
    mapOf("bookingId" to bookingId.toString(),
        "movementType" to movement.movementType,
        "fromAgency" to (movement.fromAgency ?: "not present"),
        "toAgency" to (movement.toAgency ?: "not present"))

