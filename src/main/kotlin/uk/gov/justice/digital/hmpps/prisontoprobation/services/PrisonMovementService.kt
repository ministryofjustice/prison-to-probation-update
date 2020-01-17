package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
open class PrisonMovementService(private val offenderService: OffenderService, private val telemetryClient: TelemetryClient) {
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
        if (isMovementTransferIntoPrison(movement)) {
            log.info("Movement for booking $bookingId is transfer from ${movement.fromAgency} to ${movement.toAgency}")

            val prisoner = offenderService.getOffender(movement.offenderNo)
            log.info("Prisoner is ${prisoner.offenderNo} latest location ${prisoner.latestLocation}")
            telemetryClient.trackEvent("P2PTransferIn",
                    mapOf(
                            "bookingId" to bookingId.toString(),
                            "fromAgency" to movement.fromAgency,
                            "toAgency" to movement.toAgency,
                            "offenderNo" to prisoner.offenderNo,
                            "firstName" to prisoner.firstName,
                            "lastName" to prisoner.lastName,
                            "latestLocation" to prisoner.latestLocation,
                            "convictedStatus" to prisoner.convictedStatus
                    ),
                    null)
        } else {
            telemetryClient.trackEvent("P2PTransferIgnored",
                    mapOf(
                            "bookingId" to bookingId.toString(),
                            "movementType" to movement.movementType,
                            "fromAgency" to movement.fromAgency,
                            "toAgency" to movement.toAgency),
                    null)
            log.info("Movement for booking $bookingId will be ignored, type is ${movement.movementType} from  ${movement.fromAgency} to ${movement.toAgency}")
        }

    }

    private fun isMovementTransferIntoPrison(movement: Movement): Boolean {
        return movement.movementType == "ADM"
    }

}

