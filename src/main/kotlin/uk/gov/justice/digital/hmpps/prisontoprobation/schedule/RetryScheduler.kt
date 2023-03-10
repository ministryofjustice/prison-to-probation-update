package uk.gov.justice.digital.hmpps.prisontoprobation.schedule

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageRetryService

@Service
@ConditionalOnProperty(name = ["prisontoprobation.message-processor.enabled"], havingValue = "true")
class RetryScheduler(val messgaeRetryService: MessageRetryService) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${retry.schedules.short.cron}")
  @SchedulerLock(name = "scheduleLock")
  fun retryShortTerm() {
    log.debug("Running retry for short term")
    messgaeRetryService.retryShortTerm()
  }
  @Scheduled(cron = "\${retry.schedules.medium.cron}")
  @SchedulerLock(name = "scheduleLock")
  fun retryMediumTerm() {
    log.debug("Running retry for medium term")
    messgaeRetryService.retryMediumTerm()
  }

  @Scheduled(cron = "\${retry.schedules.long.cron}")
  @SchedulerLock(name = "scheduleLock")
  fun retryLongTerm() {
    log.debug("Running retry for long term")
    messgaeRetryService.retryLongTerm()
  }
}
