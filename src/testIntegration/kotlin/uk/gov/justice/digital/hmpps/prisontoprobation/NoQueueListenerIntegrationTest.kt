package uk.gov.justice.digital.hmpps.prisontoprobation

import org.springframework.test.context.ActiveProfiles

@ActiveProfiles(profiles = ["no-queue-listener"])
class NoQueueListenerIntegrationTest : IntegrationTest()
