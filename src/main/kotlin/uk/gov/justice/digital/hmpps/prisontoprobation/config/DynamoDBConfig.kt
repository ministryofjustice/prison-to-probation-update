package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
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
        .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(tableAccessKeyId, tableSecretAccessKey)))
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
      log.info("Created main DynamoDB table $tableName")
      return dynamoDB
    }
  }

  @Bean("scheduleDynamoDB")
  @ConditionalOnProperty(name = ["hmpps.dynamodb.provider"], havingValue = "aws")
  fun amazonScheduleDynamoDB(dynamoDbConfigProperties: DynamoDbConfigProperties): AmazonDynamoDB =
    with(dynamoDbConfigProperties) {
      AmazonDynamoDBClientBuilder
        .standard()
        .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(scheduleTableAccessKeyId, scheduleTableSecretAccessKey)))
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
        log.info("Created DynamoDB lock table $tableName")
      } catch (e: ResourceInUseException) {
        log.warn("We are using a random lock table name within each Spring context - so not expecting tables to already exist. Please investigate!")
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

fun createTable(tableName: String, dynamoDB: AmazonDynamoDB) {
  val dynamoDBMapper = DynamoDBMapper(dynamoDB)
  val tableRequest: CreateTableRequest = dynamoDBMapper
    .generateCreateTableRequest(Message::class.java)
  tableRequest.provisionedThroughput = ProvisionedThroughput(1L, 1L)
  try {
    tableRequest.tableName = tableName
    dynamoDB.createTable(tableRequest)
    dynamoDB.updateTimeToLive(
      UpdateTimeToLiveRequest()
        .withTableName(tableName)
        .withTimeToLiveSpecification(
          TimeToLiveSpecification()
            .withAttributeName("deleteBy")
            .withEnabled(true)
        )
    )
  } catch (e: ResourceInUseException) {
    DynamoDBConfig.log.warn("We are using random table names within each Spring context - so not expecting tables to already exist. Please investigate!")
  }
}
