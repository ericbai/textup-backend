package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class NumberActionService implements HandlesActions<IndividualPhoneRecordWrapper, IndividualPhoneRecordWrapper> {

    @Override
    boolean hasActions(Map body) { !!body.doNumberActions }

    @Override
    Result<IndividualPhoneRecordWrapper> tryHandleActions(IndividualPhoneRecordWrapper w1, Map body) {
        ActionContainer.tryProcess(ContactNumberAction, body.doNumberActions)
            .then { List<ContactNumberAction> actions ->
                ResultGroup
                    .collect(actions) { ContactNumberAction a1 ->
                        switch (a1) {
                            case ContactNumberAction.MERGE:
                                w1.tryMergeNumber(a1.buildPhoneNumber(), a1.preference)
                                break
                            default: // ContactNumberAction.DELETE
                                w1.tryDeleteNumber(a1.buildPhoneNumber())
                        }
                    }
                    .toEmptyResult(false)
            }
            .then { DomainUtils.trySave(w1) }
    }
}
