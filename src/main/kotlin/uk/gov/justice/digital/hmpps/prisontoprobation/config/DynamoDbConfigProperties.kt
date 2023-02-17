package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hmpps.dynamodb")
class DynamoDbConfigProperties(
  val provider: String,
  val region: String,
  val localstackUrl: String = "",
  val tableName: String,
  val tableAccessKeyId: String = "",
  val tableSecretAccessKey: String = "",
  val scheduleTableName: String,
  val scheduleTableAccessKeyId: String = "",
  val scheduleTableSecretAccessKey: String = "",
)
