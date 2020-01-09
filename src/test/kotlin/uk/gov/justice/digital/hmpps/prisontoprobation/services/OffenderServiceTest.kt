package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.OAuth2RestTemplate

@RunWith(MockitoJUnitRunner::class)
class OffenderServiceTest {
  private val restTemplate: OAuth2RestTemplate = mock()

  private lateinit var service: OffenderService

  @Before
  fun before() {
    service = OffenderService(restTemplate)
  }

  @Test
  fun `test get offender calls rest template`() {
    val expectedOffender = createOffender()
    whenever(restTemplate.getForEntity<Offender>(anyString(), any(), anyString())).thenReturn(ResponseEntity.ok(expectedOffender))

    val note = service.getOffender("AB123D")

    assertThat(note).isEqualTo(expectedOffender)

    verify(restTemplate).getForEntity("/elite2api/api/bookings/offenderNo/{offenderId}?fullInfo=true", Offender::class.java, "AB123D")
  }


  private fun createOffender() = Offender(
          offenderId = "AB123D")
}
