package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode

@GrailsTypeChecked
@EqualsAndHashCode
class IndividualPhoneRecord extends PhoneRecord {

    boolean isDeleted = false
    String name
    String note

    static hasMany = [numbers: ContactNumber]
    static mapping = {
        numbers fetch: "join", cascade: "all-delete-orphan"
        isDeleted column: "individual_is_deleted"
        name column: "individual_name"
        note column: "individual_note", type: "text"
    }
    static constraints = {
        name blank: true, nullable: true
        note blank: true, nullable: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
    }

    static Result<IndividualPhoneRecord> tryCreate(Phone p1) {
        Record.tryCreate()
            .then { Record rec1 ->
                IndividualPhoneRecord ipr1 = new IndividualPhoneRecord(phone: p1, record: rec1)
                DomainUtils.trySave(ipr1, ResultStatus.CREATED)
            }
    }

    def beforeInsert() { normalizeNumberPreferences() }

    def beforeUpdate() { normalizeNumberPreferences() }

    // Methods
    // -------

    @Override
    boolean isActive() { super.isActive() && !isDeleted }

    @Override
    IndividualPhoneRecordWrapper toWrapper(PhoneRecord sharingOverride = null) {
        sharingOverride ?
            new IndividualPhoneRecordWrapper(this, sharingOverride.toPermissions(), sharingOverride)
            new IndividualPhoneRecordWrapper(this, toPermissions())
    }

    Result<ContactNumber> mergeNumber(BasePhoneNumber bNum, int preference) {
        ContactNumber cNum = numbers?.find { it.number == bNum.number }
        if (cNum) {
            cNum.preference = preference
            DomainUtils.trySave(cNum)
        }
        else { ContactNumber.tryCreate(this, bNum, preference) }
    }

    Result<Void> deleteNumber(BasePhoneNumber bNum) {
        ContactNumber cNum = numbers?.find { it.number == bNum?.number }
        if (cNum) {
            removeFromNumbers(cNum)
            cNum.delete()
            Result.void()
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("contact.numberNotFound",
                ResultStatus.NOT_FOUND, [bNum?.prettyPhoneNumber])
        }
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name ?: numbers?.getAt(0)?.prettyPhoneNumber ?: "" }

    List<ContactNumber> getSortedNumbers() { numbers?.sort(false) ?: [] }

    // Helpers
    // -------

    protected void normalizeNumberPreferences() {
        getSortedNumbers().eachWithIndex { ContactNumber cn1, int pref -> cn1.preference = pref }
    }
}
