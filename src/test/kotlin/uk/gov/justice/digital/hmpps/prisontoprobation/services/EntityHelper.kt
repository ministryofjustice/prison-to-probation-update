package uk.gov.justice.digital.hmpps.prisontoprobation.services

import java.time.LocalDate

fun createBooking(
  activeFlag: Boolean = true,
  agencyId: String = "MDI",
  bookingNo: String = "38339A",
  offenderNo: String = "A5089DY",
  firstName: String = "Johnny",
  lastName: String = "Barnes",
  dateOfBirth: LocalDate = LocalDate.of(1965, 7, 19)
): Booking = Booking(
  bookingNo = bookingNo,
  activeFlag = activeFlag,
  offenderNo = offenderNo,
  agencyId = agencyId,
  firstName = firstName,
  lastName = lastName,
  dateOfBirth = dateOfBirth
)
