package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isNull
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.LocalDate

internal class OffenderProbationMatchServiceTest {
  private val offenderSearchService: OffenderSearchService = mock()
  private val offenderService: OffenderService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val communityService: CommunityService = mock()
  private val service = OffenderProbationMatchService(telemetryClient, offenderSearchService, offenderService, communityService, listOf("MDI"))

  @BeforeEach
  fun setup() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(matchedBy = "NOTHING", matches = listOf()))
    whenever(offenderService.getOffender(any())).thenReturn(prisonerOf())
    whenever(communityService.getConvictions(any())).thenReturn(listOf())
  }

  @Test
  fun `will call matching service using booking and offender details`() {
    whenever(offenderService.getOffender(any())).thenReturn(prisonerOf(croNumber = "SF80/655108T", pncNumber = "18/0123456X"))
    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "AB123D",
            firstName = "John",
            lastName = "Smith",
            dateOfBirth = LocalDate.of(1965, 7, 19)
        ),
        LocalDate.parse("2020-01-30")
    )

    val matchRequestCaptor = argumentCaptor<MatchRequest>()
    verify(offenderSearchService).matchProbationOffender(matchRequestCaptor.capture())


    with(matchRequestCaptor.firstValue) {
      assertThat(this.activeSentence).isTrue()
      assertThat(this.dateOfBirth).isEqualTo(LocalDate.of(1965, 7, 19))
      assertThat(this.firstName).isEqualTo("John")
      assertThat(this.surname).isEqualTo("Smith")
      assertThat(this.nomsNumber).isEqualTo("AB123D")
      assertThat(this.croNumber).isEqualTo("SF80/655108T")
      assertThat(this.pncNumber).isEqualTo("18/0123456X")
    }

  }

  @Test
  fun `will call matching service with null keyids when not present`() {
    whenever(offenderService.getOffender(any())).thenReturn(prisonerOf(croNumber = null, pncNumber = null))
    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "AB123D",
            firstName = "John",
            lastName = "Smith",
            dateOfBirth = LocalDate.of(1965, 7, 19)
        ),
        LocalDate.parse("2020-01-30")
    )

    verify(offenderSearchService, atLeastOnce()).matchProbationOffender(check {
      assertThat(it.pncNumber).isNull()
      assertThat(it.croNumber).isNull()
    })
  }

  @Test
  fun `will return the offender number when matched to a single offender`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "ALL_SUPPLIED",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    val offenderNo = service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { Assertions.fail("should have got a result") }

    assertThat(offenderNo).isEqualTo("A5089DY")
  }

  @Test
  fun `will return ignore with P2POffenderNoMatch telemetry when no offenders found`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "NOTHING",
        matches = listOf()
    ))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    ).onIgnore {
      assertThat(it.reason.name).isEqualTo("P2POffenderNoMatch")
      return
    }

    fail("should have returned from onIgnore")
  }

  @Test
  fun `will return ignore with P2POffenderNoMatch telemetry when no offenders found with matching sentence`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("1988-01-30"))
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    ).onIgnore {
      assertThat(it.reason.name).isEqualTo("P2POffenderNoMatch")
      return
    }

    fail("should have returned from onIgnore")
  }

  @Test
  fun `will return offender number when no offenders found with matching sentence but one found with NOMS number`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "HMPPS_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("1988-01-30"))
    )))

    val offenderNo = service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { fail("should have got a result") }

    assertThat(offenderNo).isEqualTo("A5089DY")
  }

  @Test
  fun `will return offender number when no offenders found with matching sentence but one found with NOMS number even when alias is matched`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "ALL_SUPPLIED_ALIAS",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("1988-01-30"))
    )))

    val offenderNo = service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { fail("should have got a result") }

    assertThat(offenderNo).isEqualTo("A5089DY")
  }

  @Test
  fun `will return ignore with P2POffenderTooManyMatches telemetry when more than one offender found with matching sentence`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))), OffenderMatch(OffenderDetail(otherIds = IDs(crn = "Z12345"))))
    ))
    whenever(communityService.getConvictions(any())).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    ).onIgnore {
      assertThat(it.reason.name).isEqualTo("P2POffenderTooManyMatches")
      return
    }

    fail("should have returned from onIgnore")
  }


  @Test
  fun `will send telemetry event with a match summary`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))), OffenderMatch(OffenderDetail(otherIds = IDs(crn = "A12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))
    whenever(communityService.getConvictions("A12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("1970-03-29")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["matches"]).isEqualTo("2")
      assertThat(it["filtered_matches"]).isEqualTo("1")
      assertThat(it["crns"]).isEqualTo("A12345, X12345")
      assertThat(it["filtered_crns"]).isEqualTo("X12345")
      assertThat(it["offenderNo"]).isEqualTo("A5089DY")
      assertThat(it["bookingNumber"]).isEqualTo("38339A")
    }, isNull())
  }

  @Test
  fun `will not filter out offenders that have a custody sentence starting on the same date`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(matchedBy = "EXTERNAL_KEY", matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["crns"]).isEqualTo("X12345")
      assertThat(it["filtered_crns"]).isEqualTo("X12345")
    }, isNull())
  }

  @Test
  fun `will not filter out offenders that have a custody sentence starting just before the same date`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-23")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["crns"]).isEqualTo("X12345")
      assertThat(it["filtered_crns"]).isEqualTo("X12345")
    }, isNull())
  }

  @Test
  fun `will not filter out offenders that have a custody sentence starting just after the same date`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-02-06")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["crns"]).isEqualTo("X12345")
      assertThat(it["filtered_crns"]).isEqualTo("X12345")
    }, isNull())
  }

  @Test
  fun `will filter out offenders that have a custody sentence starting a completely different date`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2019-03-12")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["crns"]).isEqualTo("X12345")
      assertThat(it["filtered_crns"]).isEqualTo("")
    }, isNull())
  }

  @Test
  fun `will filter out offenders that have a non-custodial sentence starting on same date`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = null)
    ))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["crns"]).isEqualTo("X12345")
      assertThat(it["filtered_crns"]).isEqualTo("")
    }, isNull())
  }

  @Test
  fun `will not filter out offenders that have an inactive custodial sentence starting on same date which maybe recalls`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = false,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null))
    ))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["crns"]).isEqualTo("X12345")
      assertThat(it["filtered_crns"]).isEqualTo("X12345")
    }, isNull())
  }

  @Test
  fun `will filter out offenders that have no sentence`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = null
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderMatch"), check {
      assertThat(it["crns"]).isEqualTo("X12345")
      assertThat(it["filtered_crns"]).isEqualTo("")
    }, isNull())
  }

  @Test
  fun `will log differences between a NOMS number search and other id search`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(
        OffenderMatches(matchedBy = "EXTERNAL_KEY", matches = listOf(
            OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X00001"))),
            OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X00002"))),
            OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X00003")))
        ))).thenReturn(
        OffenderMatches(matchedBy = "EXTERNAL_KEY", matches = listOf(
            OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X00002"))),
            OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X00003"))),
            OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X00004")))
        ))
    )
    whenever(communityService.getConvictions(any())).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(
            offenderNo = "A5089DY",
            bookingNo = "38339A"),
        LocalDate.parse("2020-01-30")
    ).onIgnore { return }

    verify(telemetryClient).trackEvent(eq("P2POffenderImperfectMatch"), check {
      assertThat(it["extra_crns"]).isEqualTo("X00004")
      assertThat(it["crns"]).isEqualTo("X00002, X00003")
      assertThat(it["missing_crns"]).isEqualTo("X00001")
    }, isNull())
  }

  @Test
  fun `will update probation with offender number when single match found not using NOMS number`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    )

    verify(communityService).updateProbationOffenderNo("X12345", "A5089DY")
  }

  @Test
  fun `will ignore when offender found not using NOMS number but they are not in an interested prison`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY", agencyId = "XX"),
        LocalDate.parse("2020-01-30")
    ).onIgnore {
      assertThat(it.reason.name).isEqualTo("P2PChangeIgnored")
      return
    }

    fail("should have returned from onIgnore")
  }


  @Test
  fun `will send telemetry event indicating NOMS number has been updated`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    )

    verify(telemetryClient).trackEvent(eq("P2POffenderNumberSet"), check {
      assertThat(it["crn"]).isEqualTo("X12345")
      assertThat(it["offenderNo"]).isEqualTo("A5089DY")
    }, isNull())
  }

  @Test
  fun `will not update probation with offender number when match found using NOMS number and all other data`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "ALL_SUPPLIED",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    )

    verify(communityService, never()).updateProbationOffenderNo(any(), any())
  }

  @Test
  fun `will not update probation with offender number when match found using NOMS number`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "HMPPS_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))))
    ))
    whenever(communityService.getConvictions("X12345")).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    )

    verify(communityService, never()).updateProbationOffenderNo(any(), any())
  }

  @Test
  fun `will not update probation with offender number when multiple matches found`() {
    whenever(offenderSearchService.matchProbationOffender(any())).thenReturn(OffenderMatches(
        matchedBy = "EXTERNAL_KEY",
        matches = listOf(OffenderMatch(OffenderDetail(otherIds = IDs(crn = "X12345"))), OffenderMatch(OffenderDetail(otherIds = IDs(crn = "Z12345"))))
    ))
    whenever(communityService.getConvictions(any())).thenReturn(listOf(Conviction(
        index = "1",
        active = true,
        sentence = Sentence(startDate = LocalDate.parse("2020-01-30")),
        custody = Custody(institution = null, bookingNumber = null)
    )))

    service.ensureOffenderNumberExistsInProbation(
        bookingOf(offenderNo = "A5089DY"),
        LocalDate.parse("2020-01-30")
    )

    verify(communityService, never()).updateProbationOffenderNo(any(), any())
  }


  private fun bookingOf(
      bookingNo: String = "38339A",
      offenderNo: String = "A12344",
      firstName: String = "Joe",
      lastName: String = "Plumb",
      dateOfBirth: LocalDate = LocalDate.now().minusYears(20),
      agencyId: String = "MDI"
      ) = Booking(
      bookingNo = bookingNo,
      activeFlag = true,
      offenderNo = offenderNo,
      agencyId = agencyId,
      firstName = firstName,
      lastName = lastName,
      dateOfBirth = dateOfBirth)

  private fun prisonerOf(croNumber: String? = null, pncNumber: String? = null) = Prisoner(
      offenderNo = "AB123D",
      pncNumber = pncNumber,
      croNumber = croNumber,
      firstName = "",
      middleNames = "",
      lastName = "",
      dateOfBirth = "",
      currentlyInPrison = "",
      latestBookingId = 1L,
      latestLocationId = "",
      latestLocation = "",
      convictedStatus = "",
      imprisonmentStatus = "",
      receptionDate = "")

}
