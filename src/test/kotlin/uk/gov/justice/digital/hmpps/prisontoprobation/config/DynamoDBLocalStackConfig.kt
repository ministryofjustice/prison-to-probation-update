package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import net.javacrumbs.shedlock.provider.dynamodb.DynamoDBUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.testcontainers.containers.localstack.LocalStackContainer


@Configuration
@ConditionalOnProperty(name = ["dynamodb.provider"], havingValue = "embedded-localstack")
class DynamoDBLocalStackConfig(private val localStackContainer: LocalStackContainer) {


  @Bean("amazonDynamoDB")
  @Primary
  fun localstackDynamoDB(
      @Value("\${dynamodb.tableName}")
      tableName: String
  ): AmazonDynamoDB {
    val dynamoDB = AmazonDynamoDBClientBuilder
        .standard()
        .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.DYNAMODB))
        .withCredentials(localStackContainer.defaultCredentialsProvider)
        .build()
    createTable(tableName, dynamoDB)
    return dynamoDB
  }

  @Bean("scheduleDynamoDB")
  fun localstackScheduleDynamoDB(
      @Value("\${dynamodb.schedule.tableName}")
      tableName: String
  ): AmazonDynamoDB {
    val dynamoDB = AmazonDynamoDBClientBuilder
        .standard()
        .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.DYNAMODB))
        .withCredentials(localStackContainer.defaultCredentialsProvider)
        .build()
    DynamoDBUtils.createLockTable(dynamoDB, tableName, ProvisionedThroughput(1L, 1L))
    return dynamoDB
  }

}