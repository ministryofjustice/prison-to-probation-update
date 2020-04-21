package uk.gov.justice.digital.hmpps.prisontoprobation.schedule

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageRetryService


@Service
class RetryScheduler (val messgaeRetryService: MessageRetryService){
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${retry.schedule.cron}")
  @SchedulerLock(name = "scheduleLock")
  fun retry() {
    log.info("Running retry")
    messgaeRetryService.retryAll()
  }
}