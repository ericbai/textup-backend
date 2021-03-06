package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(["ROLE_USER", "ROLE_ADMIN"])
@Transactional
class DuplicateController extends BaseController {

    DuplicateService duplicateService

    @Override
    def index() {
        TypeMap qParams = TypeMap.create(params)
        ControllerUtils.tryGetPhoneId(qParams.long("teamId"))
            .then { Long pId ->
                List<Long> iprIds = qParams.typedList(Long, "ids[]")
                duplicateService.findDuplicates(pId, iprIds)
            }
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { List<MergeGroup> mgs ->
                respondWithClosures({ mgs.size() }, { mgs }, qParams, MarshallerUtils.KEY_MERGE_GROUP)
            }
    }
}
