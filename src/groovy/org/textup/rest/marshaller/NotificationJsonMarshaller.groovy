package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class NotificationJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { Notification notif1 ->
        PhoneOwnership own1 = notif1.mutablePhone.owner
        Map json = [:]
        json.with {
            id          = own1.ownerId
            name        = own1.buildName()
            phoneNumber = notif1.mutablePhone.number
            type        = own1.type.toString()
        }
        RequestUtils.tryGet(RequestUtils.STAFF_ID)
            .then { Long staffId -> Staffs.mustFindForId(staffId) }
            .logFail("NotificationJsonMarshaller: staff not found")
            .then { Staff s1 ->
                ReadOnlyOwnerPolicy rop1 = OwnerPolicies.findReadOnlyOrDefaultForOwnerAndStaff(own1, s1)
                json.details = notif1.buildAllowedItemsForOwnerPolicy(rop1)
                json.putAll(NotificationInfo.create(rop1, notif1).properties)
                Result.void()
            }
            .ifFailEnd { json.details = notif1.items }
        json
	}

	NotificationJsonMarshaller() {
		super(Notification, marshalClosure)
	}
}
