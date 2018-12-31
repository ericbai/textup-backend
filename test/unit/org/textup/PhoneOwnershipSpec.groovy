package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.rest.NotificationStatus
import org.textup.test.*
import org.textup.type.NotificationLevel
import org.textup.type.PhoneOwnershipType
import org.textup.type.StaffStatus
import spock.lang.Ignore
import spock.lang.Shared

@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
	Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
class PhoneOwnershipSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    void "test constraints"() {
    	when: "we have an empty phone ownership"
    	PhoneOwnership own = new PhoneOwnership()

    	then: "invalid"
    	own.validate() == false
    	own.errors.errorCount == 3

    	when: "we fill out fields"
    	own = new PhoneOwnership(phone:p1, type:PhoneOwnershipType.INDIVIDUAL,
    		ownerId:s1.id)

    	then: "valid"
    	own.validate() == true
    }

    void "test getting owners"() {
    	given: "team has all active staff"
    	t1.members.each { it.status = StaffStatus.STAFF }
    	t1.save(flush:true, failOnError:true)

    	when: "we have an individual phone ownership"
    	PhoneOwnership own = new PhoneOwnership(phone:p1, ownerId:s1.id,
    		type:PhoneOwnershipType.INDIVIDUAL)

    	then:
    	own.validate() == true
    	own.buildName() == s1.name
    	own.buildAllStaff().size() == 1
    	own.buildAllStaff()[0] == s1

    	when: "we have a group phone ownership"
    	own = new PhoneOwnership(phone:p1, ownerId:t1.id,
    		type:PhoneOwnershipType.GROUP)

    	then:
    	own.validate() == true
    	own.buildName() == t1.name
    	own.buildAllStaff().size() == t1.members.size()
    	own.buildAllStaff().every { Staff s1 ->
            t1.members.find { Staff s2 -> s1.id == s2.id }}
    }

    void "test building organization"() {
        when: "individual ownership"
        PhoneOwnership own = new PhoneOwnership(phone: p1, type: PhoneOwnershipType.INDIVIDUAL,
            ownerId: s1.id)

        then:
        own.buildOrganization().id == s1.org.id

        when: "group ownership"
        own.type = PhoneOwnershipType.GROUP
        own.ownerId = t1.id

        then:
        own.buildOrganization().id == t1.org.id
    }

    void "test policies"() {
        given: "a phone ownership without any notification policies"
        PhoneOwnership owner1 = p1.owner
        assert owner1.policies == null

        when: "we get or create a policy for a staff id"
        Long sId = 88L
        NotificationPolicy np1 = owner1.getOrCreatePolicyForStaff(sId)

        then: "a new, unsaved policy is created"
        np1.id == null

        when: "we try to get a policy for this same staff id"
        np1.save(flush:true, failOnError:true)
        NotificationPolicy np2 = owner1.findPolicyForStaff(np1.staffId)

        then:
        np2 != null
        np2.id == np1.id
    }

    void "test getting notification statuses"() {
        given: "notification policy for a staff "
        Staff staff1 = new Staff(username: UUID.randomUUID().toString(),
            name: "Name",
            password: "password",
            email: "hello@its.me",
            org: org,
            manualSchedule: true,
            isAvailable: false)
        staff1.save(flush:true, failOnError:true)

        Long recId = 2L
        // policy-level is available and staff-level is NOT available
        NotificationPolicy np1 = new NotificationPolicy(
            staffId: staff1.id,
            level: NotificationLevel.ALL,
            useStaffAvailability: false,
            manualSchedule: true,
            isAvailable: true)
        np1.addToBlacklist(recId)
        np1.save(flush:true, failOnError:true)

        p1.owner.addToPolicies(np1)
        p1.owner.save(flush:true, failOnError:true)

        when: "we get notification status for a staff that has policies"
        List<NotificationStatus> statuses = p1.owner
            .getNotificationStatusesForStaffsAndRecords([staff1], [recId])

        then: "policy is found and use policy-level availability"
        statuses.size() == 1
        statuses[0].staff.id == staff1.id
        statuses[0].canNotify == false
        statuses[0].validate() == true
        statuses[0].isAvailableNow == true // policy-level is available even if staff-level is not

        when: "we get notification status for staff that does not have any policies"
        assert p1.owner.findPolicyForStaff(s1.id) == null
        s1.with {
            manualSchedule = true
            isAvailable = false
        }
        p1.owner.with {
            type = PhoneOwnershipType.INDIVIDUAL
            ownerId = s1.id
        }
        [s1, p1.owner]*.save(flush:true, failOnError:true)

        statuses = p1.owner.getNotificationStatusesForStaffsAndRecords([s1], [recId])
        List<NotificationStatus> statuses2 = p1.owner.getNotificationStatusesForRecords([recId])
        List<Staff> staffList = p1.owner.getCanNotifyAndAvailable([recId])

        then: "default is permissive and uses staff-level availability"
        statuses.size() == 1
        statuses[0].staff.id == s1.id
        statuses[0].canNotify == true
        statuses[0].isAvailableNow == false
        // if not specify statuses, then use getAll()
        statuses2.size() == 1
        statuses2[0].staff.id == s1.id
        statuses2[0].canNotify == true
        statuses[0].isAvailableNow == false

        // policy is permissive, but the staff-level is NOT available
        staffList.size() == 0

        when: "the staff member is available but does not have a personal phone"
        s1.with {
            manualSchedule = true
            isAvailable = true
            personalPhoneAsString = ""
        }
        s1.save(flush:true, failOnError:true)
        statuses = p1.owner.getNotificationStatusesForStaffsAndRecords([s1], [recId])
        statuses2 = p1.owner.getNotificationStatusesForRecords([recId])
        staffList = p1.owner.getCanNotifyAndAvailable([recId])

        then: "all statuses are available, but this person cannot be notified"
        statuses.size() == 1
        statuses[0].staff.id == s1.id
        statuses[0].canNotify == true
        statuses[0].isAvailableNow == true
        // if not specify statuses, then use getAll()
        statuses2.size() == 1
        statuses2[0].staff.id == s1.id
        statuses2[0].canNotify == true
        statuses[0].isAvailableNow == true

        // policy is permissive, but the staff-level is NOT available
        staffList.size() == 0
    }
}
