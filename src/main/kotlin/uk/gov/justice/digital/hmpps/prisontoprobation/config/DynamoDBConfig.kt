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
import com.amazonaws.services.dynamodbv2.model.TimeToLiveSpecification
import com.amazonaws.services.dynamodbv2.model.UpdateTimeToLiveRequest
import net.javacrumbs.shedlock.provider.dynamodb.DynamoDBUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.prisontoprobation.entity.Message

@Configuration
class DynamoDBConfig {

  @Bean
  @Primary
  @ConditionalOnProperty(name = ["dynamodb.provider"], havingValue = "aws")
  fun amazonDynamoDB(
    @Value("\${dynamodb.aws.access.key.id}")
    amazonAWSAccessKey: String,
    @Value("\${dynamodb.aws.secret.access.key}")
    amazonAWSSecretKey: String,
    @Value("\${dynamodb.region}")
    amazonAWSRegion: String
  ): AmazonDynamoDB {
    return AmazonDynamoDBClientBuilder
      .standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(amazonAWSAccessKey, amazonAWSSecretKey)))
      .withRegion(amazonAWSRegion)
      .build()
  }

  @Bean("amazonDynamoDB")
  @Primary
  @ConditionalOnProperty(name = ["dynamodb.provider"], havingValue = "localstack")
  fun localstackDynamoDB(
    @Value("\${dynamodb.endpoint}")
    amazonDynamoDBEndpoint: String,
    @Value("\${dynamodb.region}")
    amazonDynamoDBRegion: String,
    @Value("\${dynamodb.tableName}")
    tableName: String
  ): AmazonDynamoDB {
    val dynamoDB = AmazonDynamoDBClientBuilder
      .standard()
      .withEndpointConfiguration(EndpointConfiguration(amazonDynamoDBEndpoint, amazonDynamoDBRegion))
      .build()
    createTable(tableName, dynamoDB)
    return dynamoDB
  }

  @Bean("scheduleDynamoDB")
  @ConditionalOnProperty(name = ["dynamodb.provider"], havingValue = "aws")
  fun amazonScheduleDynamoDB(
    @Value("\${dynamodb.schedule.aws.access.key.id}")
    amazonAWSAccessKey: String,
    @Value("\${dynamodb.schedule.aws.secret.access.key}")
    amazonAWSSecretKey: String,
    @Value("\${dynamodb.region}")
    amazonAWSRegion: String
  ): AmazonDynamoDB {
    return AmazonDynamoDBClientBuilder
      .standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(amazonAWSAccessKey, amazonAWSSecretKey)))
      .withRegion(amazonAWSRegion)
      .build()
  }

  @Bean("scheduleDynamoDB")
  @ConditionalOnProperty(name = ["dynamodb.provider"], havingValue = "localstack")
  fun localstackScheduleDynamoDB(
    @Value("\${dynamodb.endpoint}")
    amazonDynamoDBEndpoint: String,
    @Value("\${dynamodb.region}")
    amazonDynamoDBRegion: String,
    @Value("\${dynamodb.schedule.tableName}")
    tableName: String
  ): AmazonDynamoDB {
    val dynamoDB = AmazonDynamoDBClientBuilder
      .standard()
      .withEndpointConfiguration(EndpointConfiguration(amazonDynamoDBEndpoint, amazonDynamoDBRegion))
      .build()

    DynamoDBUtils.createLockTable(dynamoDB, tableName, ProvisionedThroughput(1L, 1L))
    return dynamoDB
  }

  @Bean
  fun tableNameOverrider(
    @Value("\${dynamodb.tableName}")
    tableName: String
  ): TableNameOverride {
    return TableNameOverride.withTableNameReplacement(tableName)
  }

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
}
