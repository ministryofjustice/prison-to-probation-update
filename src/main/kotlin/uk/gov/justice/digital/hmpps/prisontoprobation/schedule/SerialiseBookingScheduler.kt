package uk.gov.justice.digital.hmpps.prisontoprobation.schedule

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisontoprobation.services.MessageAggregator


@Service
@ConditionalOnProperty(name = ["prisontoprobation.message-processor.enabled"], havingValue = "true"  )
class SerialiseBookingScheduler (val messageAggregator: MessageAggregator){
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(fixedDelayString = "\${prisontoprobation.message-processor.delay}")
  @SchedulerLock(name = "scheduleLock")
  fun processMessages() {
    log.info("Processing messages for next booking")
    messageAggregator.processMessagesForNextBookingSets()
  }
}