package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
class IndividualPhoneRecord extends PhoneRecord {

    boolean isDeleted = false
    String name
    String note

    static hasMany = [numbers: ContactNumber]
    // `name` and `isDeleted` columns are shared with `GroupPhoneRecord`
    static mapping = {
        // [NOTE] one-to-many relationships should not have `fetch: "join"` because of GORM using
        // a left outer join to fetch the data runs into issues when a max is provided
        // see: https://stackoverflow.com/a/25426734
        numbers cascade: "all-delete-orphan"
        note column: "individual_note", type: "text"
    }
    static constraints = {
        name blank: true, nullable: true
        note blank: true, nullable: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
    }

    static Result<IndividualPhoneRecord> tryCreate(Phone p1) {
        Record.tryCreate(p1?.language)
            .then { Record rec1 ->
                IndividualPhoneRecord ipr1 = new IndividualPhoneRecord(phone: p1, record: rec1)
                DomainUtils.trySave(ipr1, ResultStatus.CREATED)
                    .ifFailAndPreserveError { rec1.delete() }
            }
    }

    def beforeInsert() { normalizeNumberPreferences() }

    def beforeUpdate() { normalizeNumberPreferences() }

    // Methods
    // -------

    @Override
    boolean isNotExpired() { super.isNotExpired() && !isDeleted }

    @Override
    IndividualPhoneRecordWrapper toWrapper(PhoneRecord sharingOverride = null) {
        sharingOverride ?
            new IndividualPhoneRecordWrapper(this, sharingOverride.toPermissions(), sharingOverride) :
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
        }
        DomainUtils.trySave(this).then { Result.void() }
    }

    IndividualPhoneRecordInfo toInfo() {
        IndividualPhoneRecordInfo.create(id, name, note, getSortedNumbers())
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name ?: getSortedNumbers()?.getAt(0)?.prettyPhoneNumber ?: "" }

    List<ContactNumber> getSortedNumbers() { numbers?.sort(false) ?: [] }

    // Helpers
    // -------

    protected void normalizeNumberPreferences() {
        getSortedNumbers().eachWithIndex { ContactNumber cn1, int pref -> cn1.preference = pref }
    }
}
