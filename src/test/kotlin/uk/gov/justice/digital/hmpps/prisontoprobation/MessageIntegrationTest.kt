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
import org.mockito.Mockito.mockingDetails
import org.springframework.boot.test.mock.mockito.MockBean

class MessageIntegrationTest : QueueIntegrationTest() {

    @MockBean
    private lateinit var telemetryClient: TelemetryClient


    @Test
    fun `will consume a prison movement message, update probation and create movement insights event`() {
        val message = "/messages/externalMovement.json".readResourceAsText()

        // wait until our queue has been purged
        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

        awsSqsClient.sendMessage(queueUrl, message)

        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/movement/1") } matches { it == 1 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=true") } matches { it == 1 }
        await untilCallTo { eliteRequestCountFor("/api/prisoners?offenderNo=A5089DY") } matches { it == 1 }
        await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber/38339A") } matches { it == 1 }
        await untilCallTo { mockingDetails(telemetryClient).invocations.size } matches { it == 2 }

        verify(telemetryClient).trackEvent(eq("P2PTransferProbationUpdated"), any(), isNull())
    }

    @Test
    fun `will consume a imprisonment status change message, update probation and create movement insights event`() {
        val message ="/messages/imprisonmentStatusChanged.json".readResourceAsText()

        // wait until our queue has been purged
        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

        awsSqsClient.sendMessage(queueUrl, message)

        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/sentenceDetail") } matches { it == 1 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=true") } matches { it == 1 }
        // this will be 2 while we have additional analysis code in since we do everything twice
        await untilCallTo { offenderSearchPostCountFor("/match") } matches { it == 2 }
        await untilCallTo { communityGetCountFor("/secure/offenders/crn/X142620/convictions") } matches { it == 2 }
        await untilCallTo { communityGetCountFor("/secure/offenders/crn/X181002/convictions") } matches { it == 2 }
        await untilCallTo { communityPutCountFor("/secure/offenders/nomsNumber/A5089DY/custody/bookingNumber") } matches { it == 1 }
        await untilCallTo { mockingDetails(telemetryClient).invocations.size } matches { it == 3 }

        verify(telemetryClient).trackEvent(eq("P2PImprisonmentStatusUpdated"), any(), isNull())
    }

    @Test
    fun `will consume a sentence date change message, update probation and create movement insights event`() {
        val message = "/messages/sentenceDatesChanged.json".readResourceAsText()

        // wait until our queue has been purged
        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

        awsSqsClient.sendMessage(queueUrl, message)

        await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835?basicInfo=true") } matches { it == 1 }
        await untilCallTo { eliteRequestCountFor("/api/bookings/1200835/sentenceDetail") } matches { it == 1 }
        await untilCallTo { communityPostCountFor("/secure/offenders/nomsNumber/A5089DY/bookingNumber/38339A/custody/keyDates") } matches { it == 1 }
        await untilCallTo { mockingDetails(telemetryClient).invocations.size } matches { it == 1 }

        verify(telemetryClient).trackEvent(eq("P2PSentenceDatesChanged"), any(), isNull())
    }

}

private fun String.readResourceAsText(): String {
    return MessageIntegrationTest::class.java.getResource(this).readText()
}