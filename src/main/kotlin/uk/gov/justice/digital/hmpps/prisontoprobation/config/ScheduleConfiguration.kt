package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Table
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling


@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT20M")class ScheduleConfiguration {
  @Bean
  fun tableLockProvider(
                        @Qualifier("scheduleDynamoDB")
                        scheduleDynamoDB: AmazonDynamoDB,
                        @Value("\${dynamodb.schedule.tableName}")
                        tableName: String): Table {
    return DynamoDB(scheduleDynamoDB).getTable(tableName)
  }

  @Bean
  fun lockProvider(table: Table): LockProvider {
    return DynamoDBLockProvider(table)
  }

}