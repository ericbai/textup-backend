package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.quartz.ScheduleBuilder
import org.quartz.Scheduler
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.springframework.transaction.TransactionStatus
import org.textup.jobs.FutureMessageJob
import org.textup.types.FutureMessageType
import org.textup.types.ResultType
import org.textup.util.OptimisticLockingRetry
import org.textup.validator.OutgoingMessage
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class FutureMessageService {

    AuthService authService
    MessageSource messageSource
    ResultFactory resultFactory
    Scheduler quartzScheduler
    SocketService socketService
    TokenService tokenService

	// Scheduler
	// ---------

    @Transactional(readOnly=true)
	Result schedule(FutureMessage fMsg) {
        try {
            TriggerKey trigKey = fMsg.triggerKey
            Trigger trig = quartzScheduler.getTrigger(trigKey)
            TriggerBuilder builder = trig ? trig.triggerBuilder : TriggerBuilder.newTrigger()
            Trigger newTrig = this.buildTrigger(trigKey, builder, fMsg)
            // schedule or reschedule trigger
            Date nextFire = trig ? quartzScheduler.rescheduleJob(trigKey, newTrig) :
                quartzScheduler.scheduleJob(newTrig)
            if (nextFire) { // rescheduleJob can return null if unsuccessful
                resultFactory.success()
            }
            else {
                resultFactory.failWithMessageAndStatus(INTERNAL_SERVER_ERROR,
                    "futureMessageService.schedule.unspecifiedError")
            }
        }
        catch (Throwable e) { resultFactory.failWithThrowable(e) }
    }
    @Transactional(readOnly=true)
    Result unschedule(FutureMessage fMsg) {
        try {
            if (!quartzScheduler.unscheduleJob(fMsg.triggerKey)) {
                log.debug("FutureMessageService.unschedule: tried to unschedule \
                    nonexistent trigger with key ${fMsg.triggerKey} for \
                    message with id ${fMsg.id}")
            }
            resultFactory.success()
        }
        catch (Throwable e) { resultFactory.failWithThrowable(e) }
    }
    protected Trigger buildTrigger(TriggerKey trigKey, TriggerBuilder builder,
        FutureMessage fMsg) {
        builder
            .forJob(FutureMessageJob.class.canonicalName)
            .withIdentity(trigKey)
            .startAt(fMsg.startDate?.toDate())
            .usingJobData(Constants.JOB_DATA_FUTURE_MESSAGE_KEY, fMsg.keyName)
            .usingJobData(Constants.JOB_DATA_STAFF_ID, authService.loggedInAndActive?.id)
        if (fMsg.endDate) {
            builder.endAt(fMsg.endDate.toDate())
        }
        ScheduleBuilder sBuilder = fMsg.scheduleBuilder
        if (sBuilder) {
            builder.withSchedule(sBuilder)
        }
        builder.build()
    }

    // Execute
    // -------

    @OptimisticLockingRetry
    Result<FutureMessage> markDone(String futureKey) {
        FutureMessage fMsg = FutureMessage.findByKeyName(futureKey)
        if (!fMsg) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "futureMessageService.markDone.messageNotFound", [futureKey])
        }
        fMsg.isDone = true
        if (fMsg.save()) {
            socketService.sendFutureMessages([fMsg]) // socketService will refresh trigger
            resultFactory.success(fMsg)
        }
        else { resultFactory.failWithValidationErrors(fMsg.errors) }
    }
    ResultList<RecordItem> execute(String futureKey, Long staffId) {
        FutureMessage fMsg = FutureMessage.findByKeyName(futureKey)
        if (!fMsg) {
            return new ResultList(resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "futureMessageService.execute.messageNotFound"))
        }
        OutgoingMessage msg = fMsg.toOutgoingMessage()
        Phone[] phones = msg.phones.toArray() as Phone[]
        if (!phones || !phones[0]) {
            return new ResultList(resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "futureMessageService.execute.phoneNotFound"))
        }
        Phone p1 = phones[0]
        // skip owner check on the phone because we may be sending through a shared contact
        // OR in the future the contact may not longer be shared with this staff member
        // but any scheduled messages that this staff member initiated should still fire
        // regardless of present sharing status
        ResultList<RecordItem> resList = p1.sendMessage(msg, Staff.get(staffId), true)
        socketService.sendItems(resList.successes.collect { Result<RecordItem> res ->
            res.payload as RecordItem
        }).logFail("FutureMessageService.execute: sending items through socket")
        // notify staffs is any successes
        if (fMsg.notifySelf && resList.isAnySuccess) {
            String instructions = messageSource.getMessage(
                "futureMessageService.notifyStaff.notification", null, LCH.getLocale())
            p1
                .getPhonesToAvailableNowForContactIds(msg.contacts*.id)
                .each { Phone p2, List<Staff> staffs ->
                    String phoneName = p2.owner.name
                    staffs.each { Staff s1 ->
                        tokenService.notifyStaff(p2, s1, fMsg.record.id, true,
                                fMsg.message, instructions)
                            .logFail("FutureMessageService.execute: calling notifyStaff")
                    }
                }
        }
        resList
    }

    // Create
    // ------

    Result<FutureMessage> createForContact(Long cId, Map body, String timezone = null) {
        this.create(Contact.get(cId)?.record, body, timezone)
    }
    Result<FutureMessage> createForSharedContact(Long scId, Map body, String timezone = null) {
        this.create(SharedContact.get(scId)?.record, body, timezone)
    }
    Result<FutureMessage> createForTag(Long ctId, Map body, String timezone = null) {
        this.create(ContactTag.get(ctId)?.record, body, timezone)
    }
    protected Result<FutureMessage> create(Record rec, Map body, String timezone = null) {
        if (!rec) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "futureMessageService.create.noRecord")
        }
        SimpleFutureMessage fMsg = new SimpleFutureMessage(record:rec)
        this.setFromBody(fMsg, body, timezone)
    }

    // Update
    // ------

    Result<FutureMessage> update(Long fId, Map body, String timezone = null) {
        FutureMessage fMsg = FutureMessage.get(fId)
        if (fMsg) {
            this.setFromBody(fMsg, body, timezone)
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "futureMessageService.update.notFound", [fId])
        }
    }

    // Delete
    // ------

    Result delete(Long fId) {
        FutureMessage fMsg = FutureMessage.get(fId)
        if (fMsg) {
            fMsg.cancel().then({
                if (fMsg.save()) {
                    resultFactory.success()
                }
                else { resultFactory.failWithValidationErrors(fMsg.errors) }
            }) as Result
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "futureMessageService.delete.notFound", [fId])
        }
    }

    // Helper Methods
    // --------------

    protected Result<FutureMessage> setFromBody(FutureMessage fMsg, Map body, String timezone = null) {
        fMsg.with {
            if (body.notifySelf != null) notifySelf = body.notifySelf
            if (body.type) {
                type = Helpers.<FutureMessageType>convertEnum(FutureMessageType, body.type)
            }
            if (body.message) message = body.message
            // optional properties
            if (body.startDate) startDate = Helpers.toDateTimeWithZone(body.startDate, timezone)
            // endDate is nullable!
            endDate = Helpers.toDateTimeWithZone(body.endDate, timezone)
        }
        if (fMsg.instanceOf(SimpleFutureMessage)) {
            SimpleFutureMessage sMsg = fMsg as SimpleFutureMessage
            // repeat count is nullable!
            sMsg.repeatCount = Helpers.toInteger(body.repeatCount)
            if (body.repeatIntervalInDays) {
                sMsg.repeatIntervalInDays = Helpers.toInteger(body.repeatIntervalInDays)
            }
        }
        // for some reason, calling save here instantly persists the message
        if (fMsg.validate()) {
            boolean isNew = !fMsg.id // is new if no id yet
            if (isNew || fMsg.shouldReschedule) {
                Result res = this.schedule(fMsg)
                if (!res.success) {
                    FutureMessage.withTransaction { TransactionStatus status ->
                        status.setRollbackOnly()
                    }
                    return res
                }
            }
            // call save finally here to persist the message
            if (fMsg.save()) {
                socketService.sendFutureMessages([fMsg]) // socketService will refresh trigger
                resultFactory.success(fMsg)
            }
            else { resultFactory.failWithValidationErrors(fMsg.errors) }
        }
        else { resultFactory.failWithValidationErrors(fMsg.errors) }
    }
}
