package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hmpps.dynamodb")
class DynamoDbConfigProperties(
  val region: String,
  val localstackUrl: String = "",
  val tableName: String,
  val scheduleTableName: String,
)
