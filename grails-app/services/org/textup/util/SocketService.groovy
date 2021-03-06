package org.textup.util

import com.pusher.rest.data.Result as PusherResult
import com.pusher.rest.Pusher
import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper
import groovy.transform.TypeCheckingMode
import org.textup.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

// [FUTURE] To ensure reliability of this info going through, pass a standardized SocketEvent object
// that contains the class and ids of the instances that have updates. Leave it to the frontend
// to fetch resolve these ids via separate GET request(s)

@GrailsTypeChecked
@Transactional(readOnly = true)
class SocketService {

    static final int DEFAULT_BATCH_SIZE = 20
    static final String EVENT_CONTACTS = "contacts"
    static final String EVENT_FUTURE_MESSAGES = "futureMessages"
    static final String EVENT_RECORD_ITEMS = "recordItems"
    static final String EVENT_PHONES = "phones"

    Pusher pusherService

    Result<Object> authenticate(String channelName, String socketId) {
        Result<Object> res = AuthUtils.tryGetAnyAuthUser()
            .then { Staff authUser ->
                if (socketId && authUser.username == SocketUtils.channelToUserName(channelName)) {
                    try {
                        String outcome = pusherService.authenticate(socketId, channelName)
                        IOCUtils.resultFactory.success(DataFormatUtils.jsonToObject(outcome))
                    }
                    catch (Throwable e) {
                        IOCUtils.resultFactory.failWithThrowable(e, "authenticate", true)
                    }
                }
                else {
                    IOCUtils.resultFactory.failWithCodeAndStatus("socketService.forbidden",
                        ResultStatus.FORBIDDEN)
                }
            }
        res
    }

    void sendItems(Collection<? extends RecordItem> items) {
        trySend(EVENT_RECORD_ITEMS,
                DEFAULT_BATCH_SIZE,
                Staffs.findEveryForRecordIds(items*.record*.id),
                items)
            .logFail("sendItems: `${items*.id}`")
    }

    void sendIndividualWrappers(Collection<? extends IndividualPhoneRecordWrapper> wraps) {
        Collection<Long> recIds = WrapperUtils.recordIdsIgnoreFails(wraps)
        trySend(EVENT_CONTACTS, 1, Staffs.findEveryForRecordIds(recIds), wraps)
            .logFail("sendIndividualWrappers: phone records `${wraps*.id}`")
    }

    void sendFutureMessages(Collection<FutureMessage> fMsgs) {
        // refresh trigger so we get the most up-to-date job detail info
        fMsgs?.each { FutureMessage fMsg -> fMsg.refreshTrigger() }
        trySend(EVENT_FUTURE_MESSAGES,
                DEFAULT_BATCH_SIZE,
                Staffs.findEveryForRecordIds(fMsgs*.record*.id),
                fMsgs)
            .logFail("sendFutureMessages: future messages `${fMsgs*.id}`")
    }

    void sendPhone(Phone p1) {
        // Once we use SocketEvents, do not need to special-case limit the payload size as we do here
        RequestUtils.trySet(RequestUtils.MOST_RECENT_MEDIA_ELEMENTS_ONLY, true)
        trySend(EVENT_PHONES, 1, p1.owner.buildAllStaff(), [p1])
            .logFail("sendPhone: phone `${p1.id}`")
    }

    // Helpers
    // -------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected Result<Void> trySend(String event, int batchSize, Collection<Staff> staffs,
        Collection<?> toSend) {

        ResultGroup<?> resGroup = new ResultGroup<>()
        if (staffs && toSend) {
            toSend // prevent exceeding payload max size
                .collate(batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE)
                .each { Collection<?> batch ->
                    Object serialized = DataFormatUtils.jsonToObject(batch)
                    staffs.each { Staff s1 ->
                        resGroup << trySendDataToStaff(event, s1, serialized)
                    }
                }
        }
        resGroup.toEmptyResult(false)
    }

    protected Result<Void> trySendDataToStaff(String event, Staff s1, Object data) {
        try {
            String channelName = SocketUtils.channelName(s1)
            PusherResult pRes = pusherService.get("/channels/$channelName".toString())
            if (pRes.status == PusherResult.Status.SUCCESS) {
                Map channelInfo = DataFormatUtils.jsonToObject(pRes.message) as Map
                if (channelInfo.occupied) {
                    pRes = pusherService.trigger(channelName, event, data)
                }
            }

            if (pRes.status == PusherResult.Status.SUCCESS) {
                Result.void()
            }
            else {
                log.error("trySendDataToStaff: ${pRes.properties}")
                IOCUtils.resultFactory.failForPusher(pRes)
            }
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "trySendDataToStaff", true)
        }
    }
}
