package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.Health.Builder
import org.springframework.boot.actuate.health.HealthIndicator


abstract class DynamoDBHealthCheck(private val dynamoDB: AmazonDynamoDB, private val tableName: String) : HealthIndicator {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun health(): Health {
    return try {
      val result = dynamoDB.describeTable(tableName)
      Builder().up().withDetails(mutableMapOf<String, Any?>(
          "tableName" to result.table.tableName,
          "rows" to result.table.itemCount,
          "tableStatus" to result.table.tableStatus
      )).build()
    } catch (e: Exception) {
      log.error("Unable to retrieve table details $tableName due to exception:", e)
      return Builder().down().withException(e).build()
    }
  }
}