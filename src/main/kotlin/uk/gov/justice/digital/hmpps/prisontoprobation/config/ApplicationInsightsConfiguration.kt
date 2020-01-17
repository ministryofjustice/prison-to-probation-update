package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.*
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Application insights now controlled by the spring-boot-starter dependency.  However when the key is not specified
 * we don't get a telemetry bean and application won't start.  Therefore need this backup configuration.
 */
@Configuration
open class ApplicationInsightsConfiguration {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }


    @Bean
    @Conditional(AppInsightKeyAbsentCondition::class)
    open fun telemetryClient(): TelemetryClient {
        log.info("Creating TelemetryClient since no appinsights.instrumentationkey found")
        return TelemetryClient()
    }

    class AppInsightKeyAbsentCondition : Condition {

        override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
            val telemetryKey: String? = context.environment.getProperty("appinsights.instrumentationkey")
            val noAppInsightsKey = telemetryKey.isNullOrBlank()
            log.info("app insights key has blank status $noAppInsightsKey, length was ${telemetryKey?.length}")
            return noAppInsightsKey
        }
    }
}