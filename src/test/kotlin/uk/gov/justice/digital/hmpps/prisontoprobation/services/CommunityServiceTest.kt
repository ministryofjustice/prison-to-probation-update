@file:Suppress("DEPRECATION")

package uk.gov.justice.digital.hmpps.prisontoprobation.services

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CommunityServiceTest {
  private val restTemplate: OAuth2RestTemplate = mock()

  private lateinit var service: CommunityService

  @BeforeEach
  fun before() {
    service = CommunityService(restTemplate)
  }

  @Nested
  inner class WhenUpdateCustody {

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
  }

  @Nested
  inner class WhenUpdateCustodyBookingNumber {

    @Test
    fun `test put custody booking number calls rest template`() {
      val expectedUpdatedCustody = createUpdatedCustody()
      whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString())).thenReturn(ResponseEntity.ok(expectedUpdatedCustody))

      val updatedCustody = service.updateProbationCustodyBookingNumber("AB123D", UpdateCustodyBookingNumber(
          sentenceStartDate = LocalDate.now(),
          bookingNumber = "38353A"
      )
      )

      assertThat(updatedCustody).isEqualTo(expectedUpdatedCustody)

      verify(restTemplate).exchange(
          "/secure/offenders/nomsNumber/{nomsNumber}/custody/bookingNumber",
          HttpMethod.PUT,
          HttpEntity(UpdateCustodyBookingNumber(
              sentenceStartDate = LocalDate.now(),
              bookingNumber = "38353A"
          )
          ),
          Custody::class.java,
          "AB123D"
      )
    }

    @Test
    fun `test custody will be null if not found`() {
      whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString())).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))

      val updatedCustody = service.updateProbationCustodyBookingNumber("AB123D", createUpdatedCustodyBookingNumber())

      assertThat(updatedCustody).isNull()
    }

    @Test
    fun `test will throw exception for other types of http responses`() {
      whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString())).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))

      assertThatThrownBy { service.updateProbationCustodyBookingNumber("AB123D", createUpdatedCustodyBookingNumber()) }.isInstanceOf(HttpClientErrorException::class.java)
    }
  }

  @Nested
  inner class WhenReplaceProbationCustodyKeyDates {

    @Test
    fun `test post key dates calls rest template`() {
      val expectedUpdatedCustody = createUpdatedCustody()
      whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString(), anyString())).thenReturn(ResponseEntity.ok(expectedUpdatedCustody))

      val replaceCustodyKeyDates = createReplaceCustodyKeyDates()
      val updatedCustody = service.replaceProbationCustodyKeyDates("AB123D", "38353A", replaceCustodyKeyDates)

      assertThat(updatedCustody).isEqualTo(expectedUpdatedCustody)

      verify(restTemplate).exchange("/secure/offenders/nomsNumber/{nomsNumber}/bookingNumber/{bookingNo}/custody/keyDates", HttpMethod.POST, HttpEntity(replaceCustodyKeyDates), Custody::class.java, "AB123D", "38353A")
    }

    @Test
    fun `test custody will be null if not found`() {
      whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString(), anyString())).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))

      val updatedCustody = service.replaceProbationCustodyKeyDates("AB123D", "38353A", createReplaceCustodyKeyDates())

      assertThat(updatedCustody).isNull()
    }

    @Test
    fun `test will throw exception for other types of http responses`() {
      whenever(restTemplate.exchange(anyString(), any(), any(), eq(Custody::class.java), anyString(), anyString())).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))

      assertThatThrownBy {  service.replaceProbationCustodyKeyDates("AB123D", "38353A", createReplaceCustodyKeyDates()) }.isInstanceOf(HttpClientErrorException::class.java)
    }
  }

  private fun createUpdatedCustody() = Custody(
      institution = Institution("Doncaster"),
      bookingNumber = "38353A"
  )

  private fun createUpdateCustody(nomsPrisonInstitutionCode: String = "MDI") = UpdateCustody(
      nomsPrisonInstitutionCode = nomsPrisonInstitutionCode
  )

  private fun createUpdatedCustodyBookingNumber() = UpdateCustodyBookingNumber(
      sentenceStartDate = LocalDate.now(),
      bookingNumber = "38353A"
  )

  private fun createReplaceCustodyKeyDates() = ReplaceCustodyKeyDates()
}
