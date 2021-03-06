package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.joda.time.DateTime
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class FutureMessageService implements ManagesDomain.Creater<FutureMessage>, ManagesDomain.Updater<FutureMessage>, ManagesDomain.Deleter {

    FutureMessageJobService futureMessageJobService
    MediaService mediaService
    SocketService socketService

    @RollbackOnResultFailure
    Result<FutureMessage> tryCreate(Long prId, TypeMap body) {
        Future<?> future
        PhoneRecordWrappers.mustFindForId(prId)
            .then { PhoneRecordWrapper w1 -> w1.tryGetRecord() }
            .then { Record rec1 -> mediaService.tryCreate(body).curry(rec1) }
            .then { Record rec1, Tuple<MediaInfo, Future<?>> tup1->
                Tuple.split(tup1) { MediaInfo mInfo, Future<?> fut1 ->
                    future = fut1
                    SimpleFutureMessage.tryCreate(rec1,
                        body.enum(FutureMessageType, "type"),
                        body.trimmedString("message"),
                        mInfo)
                }
            }
            .then { FutureMessage fMsg -> trySetFields(fMsg, body, body.string("timezone")) }
            .then { FutureMessage fMsg -> trySchedule(true, fMsg) }
            .then { FutureMessage fMsg -> DomainUtils.trySave(fMsg, ResultStatus.CREATED) }
            .ifFailAndPreserveError { future?.cancel(true) }
    }

    @RollbackOnResultFailure
    Result<FutureMessage> tryUpdate(Long fId, TypeMap body) {
        Future<?> future
        FutureMessages.mustFindForId(fId)
            // process media first to add in media info object BEFORE validation
            .then { FutureMessage fMsg ->
                mediaService.tryCreateOrUpdate(fMsg, body).curry(fMsg)
            }
            .then { FutureMessage fMsg, Future<?> fut1 ->
                future = fut1
                trySetFields(fMsg, body, body.string("timezone"))
            }
            .then { FutureMessage fMsg -> trySchedule(false, fMsg) }
            .then { FutureMessage fMsg -> DomainUtils.trySave(fMsg) }
            .ifFailAndPreserveError { future?.cancel(true) }
    }

    @RollbackOnResultFailure
    Result<Void> tryDelete(Long fId) {
        FutureMessages.mustFindForId(fId)
            .then { FutureMessage fMsg -> futureMessageJobService.tryUnschedule(fMsg).curry(fMsg) }
            .then { FutureMessage fMsg ->
                fMsg.isDone = true
                DomainUtils.trySave(fMsg)
            }
    }

    // Helpers
    // -------

    protected Result<FutureMessage> trySetFields(FutureMessage fMsg, TypeMap body,
        String timezone = null) {

        fMsg.with {
            if (body.notifySelfOnSend != null) notifySelfOnSend = body.boolean("notifySelfOnSend")
            if (body.type) type = body.enum(FutureMessageType, "type")
            if (body.message != null) message = body.trimmedString("message")
            if (body.startDate) startDate = body.dateTime("startDate", timezone)
            // don't wrap endDate setter in if statement because we want to support nulling
            // endDate by omitting it from the passed-in body
            endDate = body.dateTime("endDate", timezone)
        }
        // we're updating the future message's copy of the language, not the record's language
        if (body.language) fMsg.language = body.enum(VoiceLanguage, "language")

        if (fMsg.instanceOf(SimpleFutureMessage)) {
            SimpleFutureMessage sMsg = fMsg as SimpleFutureMessage
            sMsg.with {
                repeatCount = body.int("repeatCount")
                if (body.repeatIntervalInDays) repeatIntervalInDays = body.int("repeatIntervalInDays")
            }
        }
        // if timezone is provided, determine if we need to schedule a date to adjust to
        // account for daylight savings time
        if (timezone) {
            fMsg.checkScheduleDaylightSavingsAdjustment(JodaUtils.getZoneFromId(timezone))
        }
        DomainUtils.trySave(fMsg)
    }

    protected Result<FutureMessage> trySchedule(boolean isNew, FutureMessage fMsg) {
        futureMessageJobService.trySchedule(isNew, fMsg)
            .then {
                socketService.sendFutureMessages([fMsg]) // will refresh trigger
                DomainUtils.trySave(fMsg)
            }
    }
}
