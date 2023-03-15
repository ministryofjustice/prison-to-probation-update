package uk.gov.justice.digital.hmpps.prisontoprobation.services

import java.time.LocalDate

fun createBooking(
  activeFlag: Boolean = true,
  agencyId: String = "MDI",
  locationDescription: String = "HMP Moorland",
  bookingNo: String = "38339A",
  offenderNo: String = "A5089DY",
  firstName: String = "Johnny",
  lastName: String = "Barnes",
  dateOfBirth: LocalDate = LocalDate.of(1965, 7, 19),
  recall: Boolean = false,
  legalStatus: String = "SENTENCED",
): Booking = Booking(
  bookingNo = bookingNo,
  activeFlag = activeFlag,
  offenderNo = offenderNo,
  agencyId = agencyId,
  firstName = firstName,
  lastName = lastName,
  dateOfBirth = dateOfBirth,
  recall = recall,
  locationDescription = locationDescription,
  legalStatus = legalStatus,
)
