package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
open class PrisonMovementService(private val offenderService: OffenderService, private val communityService: CommunityService, private val telemetryClient: TelemetryClient) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    open fun checkMovementAndUpdateProbation(prisonerMovementMessage: ExternalPrisonerMovementMessage) {
        val (bookingId, movementSeq) = prisonerMovementMessage

        log.info("External movement for booking $bookingId with sequence $movementSeq")
        telemetryClient.trackEvent("P2PExternalMovement",
                mapOf(
                        "bookingId" to bookingId.toString(),
                        "movementSeq" to movementSeq.toString()),
                null)

        val movement = offenderService.getMovement(bookingId, movementSeq)
        if (movement != null) {
            if (isMovementTransferIntoPrison(movement)) {
                val booking = offenderService.getBooking(bookingId)

                if (booking.activeFlag) {
                    val prisoner = offenderService.getOffender(movement.offenderNo)
                    telemetryClient.trackEvent("P2PTransferIn",
                            mapOf(
                                    "bookingId" to bookingId.toString(),
                                    "bookingNumber" to booking.bookingNo,
                                    "fromAgency" to movement.fromAgency,
                                    "toAgency" to movement.toAgency,
                                    "offenderNo" to prisoner.offenderNo,
                                    "firstName" to prisoner.firstName,
                                    "lastName" to prisoner.lastName,
                                    "latestLocation" to prisoner.latestLocation,
                                    "convictedStatus" to prisoner.convictedStatus
                            ),
                            null)
                    val updatedCustody = communityService.updateProbationCustody(
                            prisoner.offenderNo,
                            booking.bookingNo,
                            UpdateCustody(movement.toAgency))
                    if (updatedCustody != null) {
                        telemetryClient.trackEvent("P2PTransferProbationUpdated",
                                mapOf(
                                        "bookingId" to bookingId.toString(),
                                        "bookingNumber" to booking.bookingNo,
                                        "toAgencyDescription" to updatedCustody.institution.description,
                                        "offenderNo" to prisoner.offenderNo
                                ),
                                null)
                    } else {
                        telemetryClient.trackEvent("P2PTransferProbationRecordNotFound",
                                mapOf(
                                        "bookingId" to bookingId.toString(),
                                        "bookingNumber" to booking.bookingNo,
                                        "offenderNo" to prisoner.offenderNo
                                ),
                                null)
                    }
                } else {
                    telemetryClient.trackEvent("P2PTransferIgnored",
                            mapOf(
                                    "bookingId" to bookingId.toString(),
                                    "movementType" to movement.movementType,
                                    "fromAgency" to movement.fromAgency,
                                    "toAgency" to movement.toAgency,
                                    "reason" to "Not an active booking"),
                            null)
                }
            } else {
                telemetryClient.trackEvent("P2PTransferIgnored",
                        mapOf(
                                "bookingId" to bookingId.toString(),
                                "movementType" to movement.movementType,
                                "fromAgency" to movement.fromAgency,
                                "toAgency" to movement.toAgency,
                                "reason" to "Not an admission"),
                        null)
            }
        } else {
            log.info("No movement found for booking $bookingId with sequence $movementSeq. Assuming booking is no longer active")
        }

    }

    private fun isMovementTransferIntoPrison(movement: Movement): Boolean {
        return movement.movementType == "ADM"
    }

}

