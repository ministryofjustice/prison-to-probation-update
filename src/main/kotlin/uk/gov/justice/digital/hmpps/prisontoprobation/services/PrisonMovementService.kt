package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Ignore
import uk.gov.justice.digital.hmpps.prisontoprobation.services.Result.Success

@Service
class PrisonMovementService(
  private val offenderService: OffenderService,
  private val communityService: CommunityService,
  private val telemetryClient: TelemetryClient,
  private val unretryableEventMetricsService: UnretryableEventMetricsService,
  @Value("\${prisontoprobation.only.prisons}") private val allowedPrisons: List<String>
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun validateMovement(message: ExternalPrisonerMovementMessage): MessageResult {
    val (bookingId, movementSeq) = message
    val movement = validMovementOf(bookingId, movementSeq).onIgnore { return Done(it.reason) }
    validToAgencyForPrisonTransfer(movement).onIgnore { return Done(it.reason) }
    validActiveBooking(bookingId).onIgnore { return Done(it.reason) }
    return TryLater(message.bookingId)
  }

  fun processMovementAndUpdateProbation(prisonerMovementMessage: ExternalPrisonerMovementMessage): MessageResult {
    val (bookingId, movementSeq) = prisonerMovementMessage
    val trackingAttributes = mapOf(
      "bookingId" to bookingId.toString(),
      "movementSeq" to movementSeq.toString()
    )

    log.info("External movement for booking $bookingId with sequence $movementSeq")
    telemetryClient.trackEvent("P2PExternalMovement", trackingAttributes, null)

    val (name, attributes) = processMovement(bookingId, movementSeq, trackingAttributes)

    telemetryClient.trackEvent(name, attributes, null)

    return Done()
  }

  private fun processMovement(bookingId: Long, movementSeq: Long, trackingAttributes: Map<String, String>): TelemetryEvent {
    val movement = movementOf(bookingId, movementSeq, trackingAttributes).onIgnore { return it.reason }

    val movementTrackingAttributes = movementTrackingAttributesFor(bookingId, movement)

    val toAgency = toAgencyForPrisonTransfer(movement, movementTrackingAttributes).onIgnore { return it.reason }
    val booking = activeBooking(bookingId, movementTrackingAttributes).onIgnore { return it.reason }
    val prisoner = offenderService.getOffender(movement.offenderNo)

    val updateTrackingAttributes = updateTrackingAttributesFor(movementTrackingAttributes, prisoner)

    unretryableEventMetricsService.movementReceived()

    return communityService.updateProbationCustody(prisoner.offenderNo, booking.bookingNo, UpdateCustody(toAgency))?.let {
      unretryableEventMetricsService.movementSucceeded()
      TelemetryEvent(
        "P2PTransferProbationUpdated",
        updateTrackingAttributes + (
          "toAgencyDescription" to (
            it.institution?.description
              ?: "Not known"
            )
          )
      )
    } ?: let {
      unretryableEventMetricsService.movementFailed()
      TelemetryEvent("P2PTransferProbationRecordNotFound", updateTrackingAttributes)
    }
  }

  private fun toAgencyForPrisonTransfer(movement: Movement, trackingAttributes: Map<String, String>): Result<String, TelemetryEvent> =
    Success(
      validToAgencyForPrisonTransfer(movement)
        .onIgnore { return Ignore(TelemetryEvent("P2PTransferIgnored", trackingAttributes + ("reason" to it.reason))) }
    )

  private fun validToAgencyForPrisonTransfer(movement: Movement): Result<String, String> =
    movement.toAgency?.takeIf { isMovementTransferIntoPrison(movement) }
      ?.let {
        if (isMovementToInterestedPrison(it)) {
          Success(it)
        } else {
          Ignore("Not an interested prison")
        }
      }
      ?: Ignore("Not a transfer")

  private fun movementOf(bookingId: Long, movementSeq: Long, trackingAttributes: Map<String, String>): Result<Movement, TelemetryEvent> =
    Success(
      validMovementOf(bookingId, movementSeq)
        .onIgnore { return Ignore(TelemetryEvent("P2PTransferIgnored", trackingAttributes + ("reason" to it.reason))) }
    )

  private fun validMovementOf(bookingId: Long, movementSeq: Long): Result<Movement, String> {
    val movement = offenderService.getMovement(bookingId, movementSeq)

    return movement?.let { Success(movement) }
      ?: Ignore("Movement not found")
  }

  private fun activeBooking(bookingId: Long, trackingAttributes: Map<String, String>): Result<Booking, TelemetryEvent> =
    Success(
      validActiveBooking(bookingId)
        .onIgnore { return Ignore(TelemetryEvent("P2PTransferIgnored", trackingAttributes + ("reason" to it.reason))) }
    )

  private fun validActiveBooking(bookingId: Long): Result<Booking, String> =
    offenderService.getBooking(bookingId).takeIf { it.activeFlag }?.let { Success(it) }
      ?: Ignore("Not an active booking")

  private fun isMovementToInterestedPrison(toAgency: String?) =
    allowAnyPrison() || allowedPrisons.contains(toAgency)

  private fun allowAnyPrison() = allowedPrisons.isEmpty()
}

private fun isMovementTransferIntoPrison(movement: Movement) =
  movement.movementType == "ADM"

private fun updateTrackingAttributesFor(movementAttributes: Map<String, String>, prisoner: Prisoner) =
  movementAttributes + mapOf(
    "offenderNo" to prisoner.offenderNo,
    "latestLocation" to prisoner.latestLocation,
    "convictedStatus" to prisoner.convictedStatus
  )

private fun movementTrackingAttributesFor(bookingId: Long, movement: Movement) =
  mapOf(
    "bookingId" to bookingId.toString(),
    "movementType" to movement.movementType,
    "fromAgency" to (movement.fromAgency ?: "not present"),
    "toAgency" to (movement.toAgency ?: "not present")
  )
