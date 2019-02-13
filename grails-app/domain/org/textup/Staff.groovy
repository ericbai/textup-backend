package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Staff implements WithId, CanSave<Staff>, ReadOnlyStaff {

    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    DateTime whenCreated = JodaUtils.utcNow()
    Organization org
    StaffStatus status
    String email
    String lockCode = Constants.DEFAULT_LOCK_CODE
    String name
    String password
    String personalNumberAsString
    String username

    static transients = ["personalNumber"]
    static mapping = {
        cache usage: "read-write", include: "non-lazy"
        whenCreated type: PersistentDateTime
        password column: "`password`"
    }
	static constraints = {
		username blank: false, unique: true, validator: { String val ->
            if (!ValidationUtils.isValidForPusher(val)) { ["format"] }
        }
		password blank: false
        lockCode blank: false
		email email: true
        personalNumberAsString blank: true, nullable: true, phoneNumber: true
	}

    static Result<Staff> tryCreate(Role r1, Organization org1, String name, String un,
        String pwd, String email) {

        Staff s1 = new Staff(name: name, username: un, password: pwd, email: email, org: org1)
        s1.status = DomainUtils.isNew(org1) ? StaffStatus.ADMIN : StaffStatus.PENDING
        DomainUtils.trySave(s1)
            .then { StaffRole.tryCreate(s1, r1) }
            .then { IOCUtils.resultFactory.success(s1, ResultStatus.CREATED) }
    }

    def beforeInsert() {
        encodePassword()
        encodeLockCode()
    }

    def beforeUpdate() {
        if (isDirty("password")) {
            encodePassword()
        }
        if (isDirty("lockCode")) {
            encodeLockCode()
        }
    }

    // Properties
    // ----------

    Set<Role> getAuthorities() {
        Collection<StaffRole> srs = StaffRole.findAllByStaff(this)
        srs.collect { StaffRole sr1 -> sr1.role }.toSet()
    }

    void setUsername(String un) { username = StringUtils.cleanUsername(un) }

    void setPersonalNumber(BasePhoneNumber num) { personalNumberAsString = num?.number }

    @Override
    PhoneNumber getPersonalNumber() { PhoneNumber.create(personalNumberAsString) }

    @Override
    ReadOnlyOrganization getReadOnlyOrg() { org }

    // Helpers
    // -------

    protected void encodeLockCode() { lockCode = AuthUtils.encodeSecureString(lockCode) }

    protected void encodePassword() { password = AuthUtils.encodeSecureString(password) }
}
