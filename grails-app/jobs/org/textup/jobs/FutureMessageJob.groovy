package org.textup.jobs

import grails.compiler.GrailsCompileStatic
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

@GrailsCompileStatic
class FutureMessageJob {

	@Autowired
	FutureMessageService futureMessageService

    @Autowired
    ResultFactory resultFactory

    String group = Key.DEFAULT_GROUP

    void execute(JobExecutionContext context) {

        println "FUTURE MESSAGE JOB! execute: context: $context"

    	JobDataMap data = context.mergedJobDataMap
        String futureKey = Helpers.toString(data.get(Constants.JOB_DATA_FUTURE_MESSAGE_KEY))
        ResultList<RecordItem> resList = futureMessageService
        	.execute(futureKey, Helpers.toLong(data.get(Constants.JOB_DATA_STAFF_ID)))
        resList.logFail("FutureMessageJob.executing job")
        resList.any({ Collection successes ->
            if (!context.trigger.mayFireAgain()) { // is last fire
                futureMessageService.markDone(futureKey)
                    .logFail("FutureMessageJob: marking done")
            }
            else { resultFactory.success() }
        })
    }
}

