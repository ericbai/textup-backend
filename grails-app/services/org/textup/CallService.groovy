package org.textup

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Call
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.validator.BasePhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@Transactional
class CallService {

    ResultFactory resultFactory

    Result<TempRecordReceipt> start(BasePhoneNumber fromNum, BasePhoneNumber toNum, Map afterPickup) {
        String callback = Helpers.getWebhookLink(handle: Constants.CALLBACK_STATUS)
        doCall(fromNum, toNum, afterPickup, callback)
    }
    Result<TempRecordReceipt> start(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, Map afterPickup) {
        // if one number we can just start without any retry logic
        if (toNums?.size() <= 1) {
            start(fromNum, toNums ? toNums[0] : null, afterPickup)
        }
        // if multiple numbers, we want to add in retry logic
        // We start with the first number **that doesn't IMMEDIATELY fail**.
        // For the first number in the list that succeeds, we append the remaining numbers to try
        // as callback parameter so that the PublicRecordController can retry with
        // the remaining numbers until none are left
        else {
            List<String> toPhoneNums = toNums
                .collect { BasePhoneNumber pNum -> pNum.e164PhoneNumber }
            int numRemaining = toNums.size() - 1
            String afterPickupJson = Helpers.toJsonString(afterPickup)
            for (int i = numRemaining; i >= 0 ; i--) {
                List<String> remaining = Helpers.takeRight(toPhoneNums, i)
                BasePhoneNumber toNum = toNums[numRemaining - i]
                // If we are on the last number in the list of numbers because all of the
                // previous numbers have IMMEDIATELY failed for whatever reason, then this
                // is the same case as if we are only trying to call one number
                if (!remaining) {
                    return start(fromNum, toNum, afterPickup)
                }
                // if we still have some numbers remaining to try, then we try to start the
                // call with this number, and if it succeeds, then we can return and the call has
                // started. If it IMMEDIATELY fails, then we move onto the next number to use
                // to try to start this call.
                else {
                    String callback = Helpers.getWebhookLink(handle: Constants.CALLBACK_STATUS,
                        remaining: remaining, afterPickup: afterPickupJson)
                    Result<TempRecordReceipt> res = doCall(fromNum, toNum, afterPickup, callback)
                    if (res.success) {
                        return res
                    }
                }
            }
            resultFactory.failWithCodeAndStatus("callService.start.missingInfoOrAllFailed",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }
    Result<TempRecordReceipt> retry(BasePhoneNumber fromNum,
        List<? extends BasePhoneNumber> toNums, String apiId, Map afterPickup) {
        this.start(fromNum, toNums, afterPickup).then({ TempRecordReceipt r1 ->
            List<RecordItem> items = RecordItem.findEveryByApiId(apiId)
            RecordItem.findEveryByApiId(apiId).each { RecordItem item1 ->
                item1.addReceipt(r1)
                item1.save()
            }
            resultFactory.success(r1)
        })
    }

    // interrupt existing call with the following Twiml
    // see https://www.twilio.com/docs/voice/api/call#update-a-call-resource
    Result<Void> interrupt(String callId, Map afterPickup) {
        try {
            Call.updater(callId)
                .setUrl(Helpers.getWebhookLink(afterPickup))
                .setStatusCallback(Helpers.getWebhookLink(handle: Constants.CALLBACK_STATUS))
                .update()
            resultFactory.success()
        }
        catch (Throwable e) { resultFactory.failWithThrowable(e) }
    }

    // Helpers
    // -------

    protected Result<TempRecordReceipt> doCall(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        Map afterPickup, String callback) {
        if (!fromNum || !toNum) {
            resultFactory.failWithCodeAndStatus("callService.doCall.missingInfo",
                ResultStatus.UNPROCESSABLE_ENTITY, null)
        }
        String afterLink = Helpers.getWebhookLink(afterPickup)
        try {
            Call call = Call
                .creator(toNum.toApiPhoneNumber(), fromNum.toApiPhoneNumber(), new URI(afterLink))
                .setStatusCallback(callback)
                .create()
            TempRecordReceipt receipt = new TempRecordReceipt(apiId:call.sid)
            receipt.contactNumber = toNum
            if (receipt.validate()) {
                resultFactory.success(receipt)
            }
            else { resultFactory.failWithValidationErrors(receipt.errors) }
        }
        catch (Throwable e) {
            log.error("CallService.doCall: ${e.class}, ${e.message}")
            // if an ApiException from Twilio, then would be a validation error
            Result res = resultFactory.failWithThrowable(e)
            if (e instanceof ApiException) {
                res.status = ResultStatus.UNPROCESSABLE_ENTITY
            }
            res
        }
    }
}
