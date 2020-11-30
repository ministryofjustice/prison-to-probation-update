package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult
import com.amazonaws.services.dynamodbv2.model.TableDescription
import com.amazonaws.services.dynamodbv2.model.TableStatus.ACTIVE
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

internal class DynamoDBHealthCheckTest {
  private val dynamoDB: AmazonDynamoDB = mock()
  private val healthCheck = TestHealthCheck(dynamoDB, "my-table")

  @Test
  internal fun `will report up when can retrieve table details`() {
    whenever(dynamoDB.describeTable("my-table"))
      .thenReturn(
        DescribeTableResult().apply {
          this.table = TableDescription().withItemCount(99).withTableStatus(ACTIVE).withTableName("my-table")
        }
      )

    val result = healthCheck.health()

    assertThat(result.status).isEqualTo(Status.UP)
    assertThat(result.details).isEqualTo(mapOf("rows" to 99L, "tableName" to "my-table", "tableStatus" to "ACTIVE"))
  }

  @Test
  internal fun `will report down when can't retrieve table details`() {
    whenever(dynamoDB.describeTable("my-table"))
      .thenThrow(RuntimeException("cannot connect"))

    val result = healthCheck.health()

    assertThat(result.status).isEqualTo(Status.DOWN)
  }
}

class TestHealthCheck(dynamoDB: AmazonDynamoDB, tableName: String) : DynamoDBHealthCheck(dynamoDB, tableName)
