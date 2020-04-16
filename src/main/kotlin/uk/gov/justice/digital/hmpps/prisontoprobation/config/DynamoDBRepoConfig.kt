package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories
import org.springframework.context.annotation.Configuration


@Configuration
@EnableDynamoDBRepositories(basePackages = ["uk.gov.justice.digital.hmpps.prisontoprobation.repositories"])
class DynamoDBRepoConfig {
}