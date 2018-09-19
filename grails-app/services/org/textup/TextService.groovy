package org.textup

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Message
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.rest.TwimlBuilder
import org.textup.type.MediaVersion
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class TextService {

    LinkGenerator grailsLinkGenerator
	ResultFactory resultFactory

    Result<TempRecordReceipt> send(BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
        String message, List<MediaElement> media = []) {

        ResultGroup<Message> failResults = new ResultGroup<>()
        Result<Message> res
        for (toNum in toNums) {
            res = this.tryText(fromNum, toNum, message, media)
            //record receipt and return on first success
            if (res.success) {
                Message tMessage = res.payload
                TempRecordReceipt receipt = new TempRecordReceipt(apiId:tMessage.sid,
                    numSegments: Helpers.to(Integer, tMessage.numSegments))
                receipt.contactNumber = toNum
                if (receipt.validate()) {
                    return resultFactory.success(receipt)
                }
                else {
                    return resultFactory.failWithValidationErrors(receipt.errors)
                }
            }
            else { failResults << res }
        }
        if (!failResults.isEmpty) {
            resultFactory.failWithResultsAndStatus(failResults.failures,
                failResults.failureStatus)
        }
        else {
            resultFactory.failWithCodeAndStatus("textService.text.noNumbers",
                ResultStatus.UNPROCESSABLE_ENTITY, null)
        }
	}

    protected Result<Message> tryText(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        String message, List<MediaElement> media) {

        String callback = grailsLinkGenerator.link(namespace:"v1", resource:"publicRecord",
                action:"save", absolute:true, params:[handle:Constants.CALLBACK_STATUS])
        try {
            Message msg1 = Message
                .creator(toNum.toApiPhoneNumber(), fromNum.toApiPhoneNumber(), message)
                .setStatusCallback(callback)
                .setMediaUrl(media.collect { MediaElement e1 ->
                    e1.sendVersion?.link?.toURI()
                })
                .create()
            resultFactory.success(msg1)
        }
        catch (Throwable e) {
            log.error("TextService.tryText: ${e.class}, ${e.message}")
            // if an ApiException from Twilio, then would be a validation error
            Result res = resultFactory.failWithThrowable(e)
            if (e instanceof ApiException) {
                res.status = ResultStatus.UNPROCESSABLE_ENTITY
            }
            res
        }
    }
}
