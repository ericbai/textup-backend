package org.textup.job

import grails.compiler.GrailsTypeChecked
import java.util.concurrent.*
import org.quartz.*
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@GrailsTypeChecked
class FutureMessageJob implements Job {

	@Autowired
	FutureMessageJobService futureMessageJobService

    @Autowired
    ThreadService threadService

    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    void execute(JobExecutionContext context) {
        JobDataMap data = context.mergedJobDataMap
        String futureKey = data.getString(QuartzUtils.DATA_FUTURE_MESSAGE_KEY)
        Long staffId = data.getLong(QuartzUtils.DATA_STAFF_ID)
        // delay to allow for the future message to be saved if we are firing it immediately so we
        // are able to find the future message when we are executing in the futureMessageJobService.
        // Delegate this to a new scheduled thread instead of blocking this thread to free up
        // a scheduler thread to execute other pending jobs
        threadService.delay(1, TimeUnit.MINUTES) {
            futureMessageJobService
                .execute(futureKey, staffId)
                .logFail("FutureMessageJob.executing job")
            // try to mark done regardless of the execution succeeds or fails
            if (!context.trigger.mayFireAgain()) { // is last fire
                futureMessageJobService.markDone(futureKey)
                    .logFail("FutureMessageJob: marking done")
            }
        }
    }
}

