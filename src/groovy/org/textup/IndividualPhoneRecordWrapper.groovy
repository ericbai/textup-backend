package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode(includeFields = true)
@GrailsTypeChecked
class IndividualPhoneRecordWrapper extends PhoneRecordWrapper {

    private final IndividualPhoneRecord individualPhoneRecord
    private final PhoneRecord overridingPhoneRecord

    IndividualPhoneRecordWrapper(IndividualPhoneRecord ipr1, PhoneRecordPermissions permissions,
        PhoneRecord overrides = null) {

        super(overrides ?: ipr1, permissions)
        individualPhoneRecord = ipr1
        overridingPhoneRecord = overrides
    }

    // Methods
    // -------

    @Override
    IndividualPhoneRecordWrapper save() {
        if (individualPhoneRecord?.save()) {
            if (overridingPhoneRecord) {
                overridingPhoneRecord.save() ? this : null
            }
            else { this }
        }
        else { null }
    }

    @Override
    Result<? extends IndividualPhoneRecord> tryUnwrap() {
        permissions?.isOwner() ?
            IOCUtils.resultFactory.success(individualPhoneRecord) :
            insufficientPermission()
    }

    Result<Void> tryDelete() {
        if (permissions?.isOwner()) {
            individualPhoneRecord.isDeleted = true
            individualPhoneRecord.tryCancelFutureMessages().then { Result.void() }
        }
        else { insufficientPermission() }
    }

    @Override
    boolean isOverridden() { !!overridingPhoneRecord }

    // Getters
    // -------

    @Override
    Class getWrappedClass() { individualPhoneRecord?.class }

    Result<Boolean> tryGetIsDeleted() {
        permissions?.canView() ?
            IOCUtils.resultFactory.success(individualPhoneRecord.isDeleted) :
            insufficientPermission()
    }

    Result<PhoneRecordStatus> tryGetNote() {
        permissions?.canView() ?
            IOCUtils.resultFactory.success(individualPhoneRecord.note) :
            insufficientPermission()
    }

    Result<List<ContactNumber>> tryGetSortedNumbers() {
        permissions?.canView() ?
            IOCUtils.resultFactory.success(individualPhoneRecord.sortedNumbers) :
            insufficientPermission()
    }

    @Override
    Result<Phone> tryGetOriginalPhone() {
        permissions?.canModify() ?
            IOCUtils.resultFactory.success(individualPhoneRecord.phone) :
            insufficientPermission()
    }

    @Override
    Result<ReadOnlyPhone> tryGetReadOnlyOriginalPhone() {
        permissions?.canView() ?
            IOCUtils.resultFactory.success(individualPhoneRecord.phone) :
            insufficientPermission()
    }

    @Override
    Result<String> tryGetSecureName() {
        permissions?.canView() ?
            IOCUtils.resultFactory.success(individualPhoneRecord.secureName) :
            insufficientPermission()
    }

    @Override
    Result<String> tryGetPublicName() {
        permissions?.canView() ?
            IOCUtils.resultFactory.success(individualPhoneRecord.publicName) :
            insufficientPermission()
    }

    // Setters
    // -------

    Result<Void> trySetNameIfPresent(String name) {
        if (!name) {
            return Result.void()
        }
        if (permissions?.canModify()) {
            individualPhoneRecord.name = name
            Result.void()
        }
        else { insufficientPermission() }
    }

    Result<Void> trySetNoteIfPresent(String note) {
        if (!note) {
            return Result.void()
        }
        if (permissions?.canModify()) {
            individualPhoneRecord.note = note
            Result.void()
        }
        else { insufficientPermission() }
    }

    Result<ContactNumber> tryMergeNumber(BasePhoneNumber bNum, int preference) {
        permissions?.canModify() ?
            individualPhoneRecord.mergeNumber(bNum, preference) :
            insufficientPermission()
    }

    Result<Void> tryDeleteNumber(BasePhoneNumber bNum) {
        permissions?.canModify() ?
            individualPhoneRecord.deleteNumber(bNum) :
            insufficientPermission()
    }
}
