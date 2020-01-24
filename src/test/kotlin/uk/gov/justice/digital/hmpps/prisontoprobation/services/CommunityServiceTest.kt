@file:Suppress("DEPRECATION")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import org.springframework.web.client.HttpClientErrorException

@RunWith(MockitoJUnitRunner::class)
class CommunityServiceTest {
  private val restTemplate: OAuth2RestTemplate = mock()

  private lateinit var service: CommunityService

  @Before
  fun before() {
    service = CommunityService(restTemplate)
  }


  @Test
  fun `test put custody calls rest template`() {
    val expectedUpdatedCustody = createUpdatedCustody()
    whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString(), anyString())).thenReturn(ResponseEntity.ok(expectedUpdatedCustody))

    val updateCustody = createUpdateCustody()
    val updatedCustody = service.updateProbationCustody("AB123D", "38353A", updateCustody)

    assertThat(updatedCustody).isEqualTo(expectedUpdatedCustody)

    verify(restTemplate).exchange("/secure/offenders/nomsNumber/{nomsNumber}/custody/bookingNumber/{bookingNumber}", HttpMethod.PUT, HttpEntity(updateCustody), Custody::class.java, "AB123D", "38353A")
  }

  @Test
  fun `test get movement will be null if not found`() {
    whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString(), anyString())).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))

    val updatedCustody = service.updateProbationCustody("AB123D", "38353A", createUpdateCustody())

    assertThat(updatedCustody).isNull()
  }

  @Test
  fun `test get movement will throw exception for other types of http responses`() {
    whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString(), anyString())).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))

    assertThatThrownBy { service.updateProbationCustody("AB123D", "38353A", createUpdateCustody()) }.isInstanceOf(HttpClientErrorException::class.java)
  }


  private fun createUpdatedCustody() = Custody(
          institution = Institution("Doncaster")
  )

  private fun createUpdateCustody(nomsPrisonInstitutionCode: String = "MDI") = UpdateCustody(
          nomsPrisonInstitutionCode = nomsPrisonInstitutionCode
  )
}
