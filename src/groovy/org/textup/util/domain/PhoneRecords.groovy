package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class PhoneRecords {

    // TODO hasPermissionsForTag
    // TODO getSharedContactIdForContact
    // TODO hasPermissionsForContact
    static Result<Void> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId().then { Long authId ->
            AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0)
        }
    }

    static DetachedCriteria<PhoneRecord> buildActiveForStaffId(Long staffId) {
        new DetachedCriteria(PhoneRecord)
            .build { "in"("phone", Phones.buildAllPhonesForStaffId(staffId)) }
            .build(PhoneRecords.forActive())
    }

    static DetachedCriteria<PhoneRecord> buildActiveForRecordIds(Collection<Long> recordIds) {
        new DetachedCriteria(PhoneRecord)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
            .build(PhoneRecords.forActive())
    }

    static DetachedCriteria<PhoneRecord> buildActiveForPhoneIds(Collection<Long> phoneIds) {
        new DetachedCriteria(PhoneRecord)
            .build { CriteriaUtils.inList(delegate, "phone.id", recordIds) }
            .build(PhoneRecords.forActive())
    }

    static Closure forShareSourceIds(Collection<Long> shareSourceIds) {
        return {
            CriteriaUtils.inList(delegate, "shareSource.id", shareSourceIds)
        }
    }

    static Closure returnsPhone() {
        return {
            projections {
                property("phone")
            }
        }
    }

    static Closure returnsRecord() {
        return {
            projections {
                property("record")
            }
        }
    }

    static Closure forActive() {
        return {
            eq("isDeleted", false) // TODO does this work?
            or {
                isNull("dateExpired") // not expired if null
                gt("dateExpired", DateTime.now(DateTimeZone.UTC))
            }
            phone {
                Phones.forActive().setDelegate(delgate).call()
            }
        }
    }

    // Helpers
    // -------

    protected static DetachedCriteria<PhoneRecord> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(PhoneRecord)
            .build {
                idEq(thisId)
                "in"("phone", Phones.buildAllPhonesForStaffId(authId))
            }
            .build(PhoneRecords.forActive())
    }
}
