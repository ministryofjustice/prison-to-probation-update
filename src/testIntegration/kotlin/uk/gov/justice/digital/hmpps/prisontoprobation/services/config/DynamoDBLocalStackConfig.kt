package uk.gov.justice.digital.hmpps.prisontoprobation.services.config

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import net.javacrumbs.shedlock.provider.dynamodb.DynamoDBUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.localstack.LocalStackContainer
import uk.gov.justice.digital.hmpps.prisontoprobation.config.DynamoDbConfigProperties
import uk.gov.justice.digital.hmpps.prisontoprobation.config.createTable

@Configuration
@ConditionalOnProperty(name = ["hmpps.dynamodb.provider"], havingValue = "embedded-localstack")
class DynamoDBLocalStackConfig(private val localStackContainer: LocalStackContainer) {

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean("amazonDynamoDB")
  fun localstackDynamoDB(dynamoDbConfigProperties: DynamoDbConfigProperties): AmazonDynamoDB {
    val dynamoDB = AmazonDynamoDBClientBuilder
      .standard()
      .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.DYNAMODB))
      .withCredentials(localStackContainer.defaultCredentialsProvider)
      .build()
    createTable(dynamoDbConfigProperties.tableName, dynamoDB)
    log.info("Created localstack dynamodb table ${dynamoDbConfigProperties.tableName}")
    return dynamoDB
  }

  @Bean("scheduleDynamoDB")
  fun localstackScheduleDynamoDB(dynamoDbConfigProperties: DynamoDbConfigProperties): AmazonDynamoDB {
    val dynamoDB = AmazonDynamoDBClientBuilder
      .standard()
      .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.DYNAMODB))
      .withCredentials(localStackContainer.defaultCredentialsProvider)
      .build()
    DynamoDBUtils.createLockTable(dynamoDB, dynamoDbConfigProperties.scheduleTableName, ProvisionedThroughput(1L, 1L))
    log.info("Created localstack dynamodb lock table ${dynamoDbConfigProperties.scheduleTableName}")
    return dynamoDB
  }
}
