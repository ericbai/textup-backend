package org.textup

import com.pusher.rest.data.Result as PusherResult
import com.sendgrid.Response as SendGridResponse
import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@Log4j
class ResultFactory {

	// Success
	// -------

    public <X, Y> Result<Tuple<X, Y>> success(X first, Y second,
        ResultStatus status = ResultStatus.OK) {

        Result.createSuccess(Tuple.create(first, second), status)
    }
	public <T> Result<T> success(T payload, ResultStatus status = ResultStatus.OK) {
		Result.createSuccess(payload, status)
	}

	// Failure
	// -------

    public <T> Result<T> failWithResultsAndStatus(Collection<Result<T>> results, ResultStatus status) {
        List<String> messages = []
    	results.each { Result<?> res -> messages.addAll(res.errorMessages) }
    	Result.<T>createError(messages, status)
    }

    public <T> Result<T> failWithGroup(ResultGroup<T> resGroup) {
        failWithResultsAndStatus(resGroup.failures, resGroup.failureStatus)
    }

    public <T> Result<T> failWithCodeAndStatus(String code, ResultStatus status, List params = []) {
        Result.<T>createError([IOCUtils.getMessage(code, params)], status)
    }

	public <T> Result<T> failWithThrowable(Throwable t, String logPrefix = null,
        boolean printStackTrace = false ) {

        if (logPrefix) {
            log.error("${logPrefix}: ${t.message}")
        }
        if (printStackTrace) {
            t.printStackTrace()
        }
        Result.<T>createError([t.message], ResultStatus.INTERNAL_SERVER_ERROR)
	}

    public <T> Result<T> failWithValidationErrors(Errors errors) {
        failWithManyValidationErrors([errors])
    }

    public <T> Result<T> failWithManyValidationErrors(Collection<Errors> manyErrors) {
        List<String> messages = []
    	manyErrors.each { Errors errors ->
    		messages.addAll(errors.allErrors.collect { ObjectError e1 -> IOCUtils.getMessage(e1) })
		}
    	Result.<T>createError(messages, ResultStatus.UNPROCESSABLE_ENTITY)
    }

    // Service-specific failure
    // ------------------------

    public <T> Result<T> failForPusher(PusherResult pRes) {
    	Result.<T>createError([pRes?.message],
            // anecdotally, PusherResult may have null httpStatus when the network connection is spotty
            // and the message will be for a `java.net.UnknownHostException`
            pRes?.httpStatus ? ResultStatus.convert(pRes.httpStatus) : ResultStatus.SERVICE_UNAVAILABLE)
    }

    public <T> Result<T> failForSendGrid(SendGridResponse response) {
    	Result.<T>createError([response.body], ResultStatus.convert(response.statusCode))
    }
}
