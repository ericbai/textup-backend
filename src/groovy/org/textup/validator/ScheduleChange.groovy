package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString
import groovy.util.logging.Log4j
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.types.ScheduleStatus

@GrailsCompileStatic
@ToString
@Validateable
@Log4j
class ScheduleChange {

    ScheduleStatus type
    DateTime when //assume UTC timezone
    String timezone
    DateTimeZone tz

    static constraints = {
        type nullable:false
    	when nullable:false
    	timezone blank:true, nullable:true
    	tz nullable:true
    }

    void setTimezone(String tzId) {
    	if (tzId) {
    		try {
				this.tz = DateTimeZone.forID(tzId)
				this.timezone = tzId
			}
			catch(e) {
                log.debug("ScheduleChange.setTimezone: with tzId $tzId, \
                    error is: ${e.message}")
            }
    	}
    }

    void setWhen(DateTime w) {
        when = w?.withZone(DateTimeZone.UTC)
    }

    DateTime getWhen() {
        tz ? when?.withZone(tz) : when
    }
}