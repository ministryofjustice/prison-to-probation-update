package uk.gov.justice.digital.hmpps.prisontoprobation.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
open class PrisonMovementService(private val offenderService: OffenderService) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    open fun checkMovementAndUpdateProbation(prisonerMovementMessage: ExternalPrisonerMovementMessage) {
        val (bookingId, movementSeq) = prisonerMovementMessage

        log.info("External movement for booking $bookingId with sequence $movementSeq")

        val movement = offenderService.getMovement(bookingId, movementSeq)
        if (isMovementTransferIntoPrison(movement)) {
            log.info("Movement for booking $bookingId is transfer from ${movement.fromAgency} to ${movement.toAgency}")
            val prisoner = offenderService.getOffender(movement.offenderNo)
            log.info("Prisoner is ${prisoner.offenderNo} ${prisoner.firstName} ${prisoner.lastName} latest location ${prisoner.latestLocation}")
        } else {
            log.info("Movement for booking $bookingId will be ignored, type is ${movement.movementType} from  ${movement.fromAgency} to ${movement.toAgency}")
        }

    }

    private fun isMovementTransferIntoPrison(movement: Movement): Boolean {
        return movement.movementType == "ADM"
    }

}

