package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.TimeUnit
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.textup.cache.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class CallbackStatusService {

    CallService callService
    RecordItemReceiptCache receiptCache
    SocketService socketService
    ThreadService threadService

    // Moved creation of new thread to PublicRecordController to avoid self-calls.
    // Aspect advice is not applied on self-calls because this bypasses the proxies Spring AOP
    // relies on. See https://docs.spring.io/spring/docs/3.1.x/spring-framework-reference/html/aop.html#aop-understanding-aop-proxies
    @OptimisticLockingRetry
    void process(TypeConvertingMap params) {
        if (params?.CallSid) {
            ReceiptStatus status = ReceiptStatus.translate(params.CallStatus as String)
            Integer duration = TypeConversionUtils.to(Integer, params.CallDuration)
            // duration may be 0 if call is ended immediately
            if (status && duration != null) {
                if (params.ParentCallSid) {
                    PhoneNumber childNumber = PhoneNumber
                        .urlDecode(params[Constants.CALLBACK_CHILD_CALL_NUMBER_KEY] as String)
                    if (childNumber.validate()) {
                        handleUpdateForChildCall(params.ParentCallSid as String,
                                params.CallSid as String, childNumber, status, duration)
                            .logFail("CallbackStatusService: child call: params: $params")
                    }
                }
                else {
                    handleUpdateForParentCall(params.CallSid as String, status, duration, params)
                        .logFail("CallbackStatusService: parent call: params: $params")
                }
            }
        }
        else if (params?.MessageSid) {
            ReceiptStatus status = ReceiptStatus.translate(params.MessageStatus as String)
            if (status) {
                handleUpdateForText(params.MessageSid as String, status)
                    .logFail("CallbackStatusService: text message: params: $params")
            }
        }
    }

    // Helpers for three types of entities to update
    // ---------------------------------------------

    // From the statusCallback attribute on the original POST request to the Text resource
    protected Result<Void> handleUpdateForText(String textId, ReceiptStatus status) {
        updateExistingReceipts(textId, status)
            .then { List<RecordItemReceipt> rpts ->
                sendItemsThroughSocket(rpts)
                IOCUtils.resultFactory.success()
            }
    }

    // From statusCallback attribute on the Number verb
    protected Result<Void> handleUpdateForChildCall(String parentId, String childId,
        PhoneNumber childNumber, ReceiptStatus status, Integer duration) {

        createNewReceipts(parentId, childId, childNumber, status, duration)
            .then { List<RecordItemReceipt> rpts ->
                sendItemsThroughSocket(rpts)
                IOCUtils.resultFactory.success()
            }
    }

    // From the statusCallback attribute on the original POST request to the Call resource
    protected Result<Void> handleUpdateForParentCall(String callId, ReceiptStatus status,
        Integer duration, TypeConvertingMap params) {

        updateExistingReceipts(callId, status, duration)
            .then { List<RecordItemReceipt> rpts ->
                // try to retry parent call if failed
                if (status == ReceiptStatus.FAILED) { tryRetryParentCall(callId, params) }
                sendItemsThroughSocket(rpts)
                IOCUtils.resultFactory.success()
            }
    }

    // Shared helpers
    // --------------

    protected Result<List<RecordItemReceipt>> createNewReceipts(String parentId,
        String childId, PhoneNumber childNumber, ReceiptStatus status, Integer duration) {

        List<RecordItemReceipt> receipts = receiptCache.findReceiptsByApiId(parentId),
            childReceipts = []
        if (receipts) {
            for (RecordItemReceipt receipt in receipts) {
                RecordItemReceipt newReceipt = new RecordItemReceipt(apiId: childId,
                    contactNumber: childNumber, status: status, numBillable: duration)
                receipt.item.addToReceipts(newReceipt)
                if (!receipt.item.merge()) {
                    return IOCUtils.resultFactory.failWithValidationErrors(receipt.item.errors)
                }
                childReceipts << newReceipt
            }
        }
        IOCUtils.resultFactory.success(childReceipts)
    }

    protected Result<List<RecordItemReceipt>> updateExistingReceipts(String apiId,
        ReceiptStatus newStatus, Integer newDuration = null) {

        List<RecordItemReceipt> receipts = receiptCache.findReceiptsByApiId(apiId)
        // (1) It's okay if we don't find any receipts for a certain apiId because we aren't interested
        // in recording the status of certain messages such as notification messages we send out
        // to staff members.
        // (2) We assume that all the receipts have the same status, so we only check the status
        // of the first receipt
        if (receipts) {
            ReceiptStatus oldStatus = receipts[0]?.status
            Integer oldDuration = receipts[0]?.numBillable
            if (TwilioUtils.shouldUpdateStatus(oldStatus, newStatus) ||
                TwilioUtils.shouldUpdateDuration(oldDuration, newDuration)) {
                receipts = receiptCache.updateReceipts(receipts, newStatus, newDuration)
            }
        }
        IOCUtils.resultFactory.success(receipts)
    }

    protected void sendItemsThroughSocket(List<RecordItemReceipt> receipts) {
        // Collect item id and refetch in new thread to avoid LazyInitializationExceptions
        // caused by trying to interact with detached Hibernate objects
        Collection<Long> itemIds = receipts
            ?.collect { RecordItemReceipt rpt -> rpt.item.id }
            ?.unique()
        if (itemIds) {
            // send items after a delay because we need this current transaction to commit before
            // attempting to send the items because, in the JSON marshaller, the receipts
            // sent are the PERSISTENT values. If the receipts in the current transaction haven't
            // saved yet, then we won't be sending any of the latest updates
            threadService.delay(5, TimeUnit.SECONDS) {
                //send items with updated status through socket
                Collection<RecordItem> items = RecordItem.getAll(itemIds as Iterable<Serializable>)
                socketService.sendItems(items)
                    .logFail("CallbackStatusService.sendItemsThroughSocket: receipts: $receipts")
            }
        }
    }

    // If multiple phone numbers on a call and the status is failure, then retry the call.
    // See CallService.start for the parameters passed into the status callback
    protected void tryRetryParentCall(String callId, TypeConvertingMap params) {
        PhoneNumber fromNum = new PhoneNumber(number: params.From as String)
        List<PhoneNumber> toNums = params.list("remaining")?.collect { Object num ->
            new PhoneNumber(number: num as String)
        } ?: new ArrayList<PhoneNumber>()
        if (!toNums) {
            return
        }
        try {
            Map afterPickup = (DataFormatUtils.jsonToObject(params.afterPickup) ?: [:]) as Map
            callService
                .retry(fromNum, toNums, callId, afterPickup, params.AccountSid as String)
                .logFail("CallbackStatusService: retrying call: params: ${params}")
        }
        catch (Throwable e) {
            log.error("CallbackStatusService: retry: ${e.message}")
            e.printStackTrace()
        }
    }
}
