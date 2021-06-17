package uk.gov.justice.digital.hmpps.prisontoprobation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PrisonToProbationUpdateApplication

fun main(args: Array<String>) {
  runApplication<PrisonToProbationUpdateApplication>(*args)
}
