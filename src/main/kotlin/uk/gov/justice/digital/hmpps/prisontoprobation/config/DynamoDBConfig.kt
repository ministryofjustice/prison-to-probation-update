package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.auth.WebIdentityTokenCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.Builder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException
import com.amazonaws.services.dynamodbv2.model.TimeToLiveSpecification
import com.amazonaws.services.dynamodbv2.model.UpdateTimeToLiveRequest
import net.javacrumbs.shedlock.provider.dynamodb.DynamoDBUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message

@Configuration
class DynamoDBConfig {

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean("amazonDynamoDB")
  @Primary
  @ConditionalOnProperty(name = ["hmpps.dynamodb.provider"], havingValue = "aws")
  fun amazonDynamoDB(dynamoDbConfigProperties: DynamoDbConfigProperties): AmazonDynamoDB =
    with(dynamoDbConfigProperties) {
      AmazonDynamoDBClientBuilder
        .standard()
        .withCredentials(WebIdentityTokenCredentialsProvider.builder().roleSessionName("prison-to-probation-update").build())
        .withRegion(region)
        .build()
    }

  @Bean("amazonDynamoDB")
  @Primary
  @ConditionalOnProperty(name = ["hmpps.dynamodb.provider"], havingValue = "localstack")
  fun localstackDynamoDB(dynamoDbConfigProperties: DynamoDbConfigProperties): AmazonDynamoDB {
    with(dynamoDbConfigProperties) {
      val dynamoDB = AmazonDynamoDBClientBuilder
        .standard()
        .withEndpointConfiguration(EndpointConfiguration(localstackUrl, region))
        .build()
      createTable(tableName, dynamoDB)
      return dynamoDB
    }
  }

  @Bean("scheduleDynamoDB")
  @ConditionalOnProperty(name = ["hmpps.dynamodb.provider"], havingValue = "aws")
  fun amazonScheduleDynamoDB(dynamoDbConfigProperties: DynamoDbConfigProperties): AmazonDynamoDB =
    with(dynamoDbConfigProperties) {
      AmazonDynamoDBClientBuilder
        .standard()
        .withCredentials(WebIdentityTokenCredentialsProvider.builder().roleSessionName("prison-to-probation-update").build())
        .withRegion(region)
        .build()
    }

  @Bean("scheduleDynamoDB")
  @ConditionalOnProperty(name = ["hmpps.dynamodb.provider"], havingValue = "localstack")
  fun localstackScheduleDynamoDB(dynamoDbConfigProperties: DynamoDbConfigProperties): AmazonDynamoDB {
    with(dynamoDbConfigProperties) {
      val dynamoDB = AmazonDynamoDBClientBuilder
        .standard()
        .withEndpointConfiguration(EndpointConfiguration(localstackUrl, region))
        .build()

      try {
        DynamoDBUtils.createLockTable(dynamoDB, scheduleTableName, ProvisionedThroughput(1L, 1L))
        log.debug("Created DynamoDB lock table $scheduleTableName")
      } catch (e: ResourceInUseException) {
        log.warn("Failed to create table $scheduleTableName as it already exists - expected when running locally but could indicate an error in tests")
      }
      return dynamoDB
    }
  }

  @Bean
  fun tableNameOverrider(dynamoDbConfigProperties: DynamoDbConfigProperties): TableNameOverride =
    TableNameOverride.withTableNameReplacement(dynamoDbConfigProperties.tableName)

  @Bean
  @Primary
  fun dynamoDBMapperConfig(tableNameOverrider: TableNameOverride): DynamoDBMapperConfig {
    val builder = Builder()
    builder.tableNameOverride = tableNameOverrider
    // Sadly this is a @deprecated method but new DynamoDBMapperConfig.Builder() is incomplete compared to DynamoDBMapperConfig.DEFAULT
    @Suppress("DEPRECATION")
    return DynamoDBMapperConfig(DynamoDBMapperConfig.DEFAULT, builder.build())
  }
}

val createTableLog = LoggerFactory.getLogger("createTable")
fun createTable(tableName: String, dynamoDB: AmazonDynamoDB) {
  val dynamoDBMapper = DynamoDBMapper(dynamoDB)
  val tableRequest: CreateTableRequest = dynamoDBMapper
    .generateCreateTableRequest(Message::class.java)
  tableRequest.provisionedThroughput = ProvisionedThroughput(1L, 1L)
  try {
    tableRequest.tableName = tableName
    dynamoDB.createTable(tableRequest)
    createTableLog.debug("Created DynamoDB table $tableName")
    dynamoDB.updateTimeToLive(
      UpdateTimeToLiveRequest()
        .withTableName(tableName)
        .withTimeToLiveSpecification(
          TimeToLiveSpecification()
            .withAttributeName("deleteBy")
            .withEnabled(true),
        ),
    )
  } catch (e: ResourceInUseException) {
    createTableLog.warn("Failed to create table $tableName as it already exists - expected when running locally but could indicate an error in tests")
  }
}
