package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import java.time.LocalDateTime

class PrisonMovementServiceTest {
    private val offenderService: OffenderService = mock()

    private lateinit var service: PrisonMovementService

    @Before
    fun before() {
        service = PrisonMovementService(offenderService)
    }

    @Test
    fun `will retrieve the associated movement`() {
        whenever(offenderService.getMovement(anyLong(), anyLong())).thenReturn(createMovement())

        service.checkMovementAndUpdateProbation(ExternalPrisonerMovementMessage(12345L, 1L))

        verify(offenderService).getMovement(12345L, 1L)
    }

    private fun createMovement() = Movement(
            offenderNo = "AB123D",
            createDateTime = LocalDateTime.now(),
            fromAgency = "LEI",
            toAgency = "MDI",
            movementType = "TRN",
            directionCode = "OUT"
    )
}

