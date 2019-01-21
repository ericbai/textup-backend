package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class FutureMessageController extends BaseController {

	FutureMessageService futureMessageService

	@Transactional(readOnly=true)
    @Override
    void index() {
        Long prId = params.long("contactId") ?: params.long("tagId")
        PhoneRecords.isAllowed(prId)
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd {
                respondWithCriteria(FutureMessages.buildForPhoneRecordIds([prId]),
                    params,
                    null,
                    MarshallerUtils.KEY_FUTURE_MESSAGE)
            }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        Long id = params.long("id")
        doShow({ FutureMessages.isAllowed(id) }, { FutureMessages.mustFindForId(id) })
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_FUTURE_MESSAGE, request, futureMessageService) { TypeMap body ->
            Long prId = params.long("contactId") ?: params.long("tagId")
            PhoneRecords.isAllowed(prId)
        }
    }

    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_FUTURE_MESSAGE, request, futureMessageService) { TypeMap body ->
            FutureMessages.isAllowed(params.long("id"))
        }
    }

    @Override
    void delete() {
        doDelete(futureMessageService) { PhoneRecords.isAllowed(params.long("id")) }
    }
}
