@file:Suppress("DEPRECATION")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.mock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.security.oauth2.client.OAuth2RestTemplate

@RunWith(MockitoJUnitRunner::class)
class CommunityServiceTest {
  private val restTemplate: OAuth2RestTemplate = mock()

  private lateinit var service: CommunityService

  @Before
  fun before() {
    service = CommunityService(restTemplate)
  }

  @Test
  fun `test update probation custody does diddly squat`() {
    val offender = createOffender()

    service.updateProbationCustody(offender)

  }

  private fun createOffender() = Offender(
          offenderNo = "AB123D")
}
