package uk.gov.justice.digital.hmpps.prisontoprobation

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isNull
import com.nhaarman.mockito_kotlin.verify
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles(profiles = ["test", "test-queue"])
class ImprisonmentStatusChangeIntegrationTest : QueueIntegrationTest() {

    @Autowired
    @MockBean
    private lateinit var telemetryClient: TelemetryClient

    @Test
    fun `will consume a imprisonment status change message, update probation and create movement insights event`() {
        val message = this::class.java.getResource("/messages/imprisonmentStatusChanged.json").readText()

        // wait until our queue has been purged
        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

        awsSqsClient.sendMessage(queueUrl, message)

        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/sentenceDetail") } matches { it == 1 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=true") } matches { it == 1 }
        await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber") } matches { it == 1 }

        verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusUpdated"), any(), isNull())
    }
}
