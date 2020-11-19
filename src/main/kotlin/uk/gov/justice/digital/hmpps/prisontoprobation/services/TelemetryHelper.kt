package uk.gov.justice.digital.hmpps.prisontoprobation.services

import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun TelemetryEvent.with(booking: Booking): TelemetryEvent = TelemetryEvent(
  this.name,
  this.attributes +
    with(booking) {
      mapOf(
        "offenderNo" to offenderNo,
        "bookingNumber" to bookingNo,
        "agencyId" to (agencyId ?: "not present")
      )
    }
)

fun TelemetryEvent.with(sentenceDetail: SentenceDetail): TelemetryEvent = TelemetryEvent(
  this.name,
  this.attributes +
    with(sentenceDetail) {
      mapOf(
        "sentenceStartDate" to sentenceStartDate.asTelemetry(),
        "confirmedReleaseDate" to confirmedReleaseDate.asTelemetry(),
        "conditionalReleaseDate" to conditionalReleaseDate.asTelemetry(),
        "conditionalReleaseOverrideDate" to conditionalReleaseOverrideDate.asTelemetry(),
        "licenceExpiryDate" to licenceExpiryDate.asTelemetry(),
        "paroleEligibilityDate" to paroleEligibilityDate.asTelemetry(),
        "sentenceExpiryDate" to sentenceExpiryDate.asTelemetry(),
        "topupSupervisionExpiryDate" to topupSupervisionExpiryDate.asTelemetry(),
        "releaseDate" to releaseDate.asTelemetry()
      )
    }
)

private fun LocalDate?.asTelemetry(): String = this?.format(DateTimeFormatter.ISO_DATE) ?: "not present"
