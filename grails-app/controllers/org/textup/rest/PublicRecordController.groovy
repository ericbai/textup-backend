package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.types.ReceiptStatus
import org.textup.types.ResultType
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Secured("permitAll")
class PublicRecordController extends BaseController {

    static namespace = "v1"

    //grailsApplication from superclass
    //authService from superclass
    RecordService recordService
    CallbackService callbackService
    TwimlBuilder twimlBuilder

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }

    def save() {
        callbackService.validate(request, params).then({ ->
            if (params.handle == Constants.CALLBACK_STATUS) {
                String apiId = params.CallSid ?: params.MessageSid
                ReceiptStatus status = params.CallStatus ?
                    ReceiptStatus.translate(params.CallStatus as String) :
                    ReceiptStatus.translate(params.MessageStatus as String)
                Integer duration = Helpers.toInteger(params.CallDuration)
                // update status
                Result<Closure> res = recordService.updateStatus(status, apiId, duration)
                if (!res.success && params.ParentCallSid) {
                    res = recordService.updateStatus(status, params.ParentCallSid as String,
                        duration)
                }
                // we don't always immediately store the receipt so sometimes
                // the receipt will not be found. If we have a not found error,
                // then catch this and still return an empty response TwiML
                // because the Twilio API always expects TwiMl in the response
                if (!res.success && res.type == ResultType.MESSAGE_STATUS &&
                    (res.payload as Map).status == NOT_FOUND) {
                    res.logFail("PublicRecordController: could not find receipt")
                    handleXmlResult(twimlBuilder.noResponse())
                }
                else { handleXmlResult(res) }
            }
            else {
                handleXmlResult(callbackService.process(params))
            }
        })
    }
}
