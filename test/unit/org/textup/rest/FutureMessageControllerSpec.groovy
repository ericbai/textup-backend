package org.textup.rest

import grails.plugin.jodatime.converters.JodaConverters
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

// TODO

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(FutureMessageController)
@TestMixin(HibernateTestMixin)
class FutureMessageControllerSpec extends CustomSpec {

	// static doWithSpring = {
 //        resultFactory(ResultFactory)
 //    }

 //    FutureMessage fMsg1

 //    def setup() {
 //        super.setupData()
 //        JodaConverters.registerJsonAndXmlMarshallers()
 //        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
 //        fMsg1 = new FutureMessage(record: c1.record, type:FutureMessageType.CALL, message:"hi")
 //        fMsg1.save(flush:true, failOnError:true)
 //    }
 //    def cleanup() {
 //        super.cleanupData()
 //    }

 //    protected void mockAuth(boolean exists, boolean hasPermission) {
 //    	controller.authService = [
 //    		exists: { Class clazz, Long id -> exists },
 //    		hasPermissionsForFutureMessage: { Long id -> hasPermission }
 //    	] as AuthService
 //    }

 //    // List
 //    // ----

 //    void "test list with no ids"() {
	// 	when:
	// 	request.method = "GET"
	// 	controller.index()

	// 	then:
	// 	response.status == HttpServletResponse.SC_BAD_REQUEST
 //    }
 //    void "test list with more than 1 id"() {
 //    	when:
	// 	request.method = "GET"
	// 	params.contactId = c1.id
	// 	params.tagId = tag1.id
	// 	controller.index()

	// 	then:
	// 	response.status == HttpServletResponse.SC_BAD_REQUEST
 //    }
 //    void "test list for contact id for my contact"() {
 //    	given:
 //    	controller.authService = [hasPermissionsForContact: { Long id ->
 //    		true
	// 	}] as AuthService
	// 	fMsg1.record = c1.record
	// 	fMsg1.save(flush:true, failOnError:true)

 //    	when:
	// 	request.method = "GET"
	// 	params.contactId = c1.id
	// 	controller.index()

	// 	then:
	// 	response.status == HttpServletResponse.SC_OK
	// 	response.json.isEmpty() == false
	// 	response.json*.id.find { it == fMsg1.id }
 //    }
 //    void "test list for contact id for shared contact"() {
 //    	given:
 //    	controller.authService = [
 //    		hasPermissionsForContact: { Long id -> false },
 //    		getSharedContactIdForContact: { Long id -> sc1.id }
	// 	] as AuthService
	// 	fMsg1.record = sc1.contact.record
	// 	fMsg1.save(flush:true, failOnError:true)

 //    	when:
	// 	request.method = "GET"
	// 	params.contactId = c1.id
	// 	controller.index()

	// 	then:
	// 	response.status == HttpServletResponse.SC_OK
	// 	response.json.isEmpty() == false
	// 	response.json*.id.find { it == fMsg1.id }
 //    }
 //    void "test list for tag id"() {
 //    	given:
 //    	controller.authService = [hasPermissionsForTag: { Long id ->
 //    		true
 //    	}] as AuthService
	// 	fMsg1.record = tag1.record
	// 	fMsg1.save(flush:true, failOnError:true)

 //    	when:
	// 	request.method = "GET"
	// 	params.tagId = tag1.id
	// 	controller.index()

	// 	then:
	// 	response.status == HttpServletResponse.SC_OK
	// 	response.json.isEmpty() == false
	// 	response.json*.id.find { it == fMsg1.id }
 //    }

 //    // Show
 //    // ----

 //    void "test show nonexistent message"() {
	// 	when:
	// 	request.method = "GET"
	// 	params.id = "-88L"
	// 	controller.show()

	// 	then:
	// 	response.status == HttpServletResponse.SC_NOT_FOUND
 //    }
 //    void "test show forbidden message"() {
 //    	given:
	// 	controller.authService = [
 //    		hasPermissionsForFutureMessage: { Long id -> false }
 //    	] as AuthService

	// 	when:
	// 	request.method = "GET"
	// 	params.id = fMsg1.id
	// 	controller.show()

	// 	then:
	// 	response.status == HttpServletResponse.SC_FORBIDDEN
 //    }
 //    void "test show message"() {
 //    	given:
 //    	controller.authService = [
 //    		hasPermissionsForFutureMessage: { Long id -> true }
 //    	] as AuthService

	// 	when:
	// 	request.method = "GET"
	// 	params.id = fMsg1.id
	// 	controller.show()

	// 	then:
	// 	response.status == HttpServletResponse.SC_OK
	// 	response.json.id == fMsg1.id
 //    }

 //    // Save
 //    // ----

 //    String _calledWhichCreate
 //    protected void mockForSave() {
 //    	controller.futureMessageService = [
 //    		createForTag: { Long id, Map body, String timezone = null ->
 //    			_calledWhichCreate = "tag"
 //    			new Result(payload:fMsg1, status:ResultStatus.CREATED)
	// 		},
	// 		createForContact: { Long id, Map body, String timezone = null ->
	// 			_calledWhichCreate = "contact"
	// 			new Result(payload:fMsg1, status:ResultStatus.CREATED)
	// 		},
	// 		createForSharedContact: { Long id, Map body, String timezone = null ->
	// 			_calledWhichCreate = "sharedContact"
	// 			new Result(payload:fMsg1, status:ResultStatus.CREATED)
	// 		}
 //    	] as FutureMessageService
 //    }

 //    void "test save no ids"() {
 //    	when:
	// 	request.method = "POST"
	// 	controller.save()

	// 	then:
	// 	response.status == HttpServletResponse.SC_BAD_REQUEST
 //    }
 //    void "test save multiple ids"() {
 //    	when:
	// 	request.method = "POST"
	// 	params.contactId = c1.id
	// 	params.tagId = tag1.id
	// 	controller.save()

	// 	then:
	// 	response.status == HttpServletResponse.SC_BAD_REQUEST
 //    }
 //    void "test save for contact id for my contact"() {
 //    	given:
	// 	mockForSave()
	// 	controller.authService = [
 //    		exists: { Class clazz, Long id -> true },
 //    		hasPermissionsForContact: { Long id -> true }
 //    	] as AuthService

	// 	when:
	// 	request.json = "{'future-message':{}}"
	// 	request.method = "POST"
	// 	params.contactId = c1.id
	// 	controller.save()

	// 	then: "implicitly save for logged-in staff"
	// 	response.status == HttpServletResponse.SC_CREATED
 //        response.json.id == fMsg1.id
 //        _calledWhichCreate == "contact"
 //    }
 //    void "test save for contact id for shared contact"() {
 //    	mockForSave()
	// 	controller.authService = [
 //    		exists: { Class clazz, Long id -> true },
 //    		hasPermissionsForContact: { Long id -> false },
 //    		getSharedContactIdForContact: { Long id -> sc1.id }
 //    	] as AuthService

	// 	when:
	// 	request.json = "{'future-message':{}}"
	// 	request.method = "POST"
	// 	params.contactId = c1.id
	// 	controller.save()

	// 	then: "implicitly save for logged-in staff"
	// 	response.status == HttpServletResponse.SC_CREATED
 //        response.json.id == fMsg1.id
 //        _calledWhichCreate == "sharedContact"
 //    }
 //    void "test save for tag id"() {
 //    	given:
	// 	mockForSave()
	// 	controller.authService = [
 //    		exists: { Class clazz, Long id -> true },
 //    		hasPermissionsForTag: { Long id -> true }
 //    	] as AuthService

	// 	when:
	// 	request.json = "{'future-message':{}}"
	// 	request.method = "POST"
	// 	params.tagId = tag1.id
	// 	controller.save()

	// 	then: "implicitly save for logged-in staff"
	// 	response.status == HttpServletResponse.SC_CREATED
 //        response.json.id == fMsg1.id
 //        _calledWhichCreate == "tag"
 //    }

 //    // Update
 //    // ------

	// void "test update nonexistent message"() {
	// 	given:
	// 	mockAuth(false, false)

	// 	when:
	// 	request.json = "{'future-message':{}}"
	// 	request.method = "PUT"
	// 	params.id = fMsg1.id
	// 	controller.update()

	// 	then:
	// 	response.status == HttpServletResponse.SC_NOT_FOUND
	// }
	// void "test update forbidden message"() {
	// 	given:
	// 	mockAuth(true, false)

	// 	when:
	// 	request.json = "{'future-message':{}}"
	// 	request.method = "PUT"
	// 	params.id = fMsg1.id
	// 	controller.update()

	// 	then:
	// 	response.status == HttpServletResponse.SC_FORBIDDEN
	// }
	// void "test update message"() {
	// 	given:
	// 	mockAuth(true, true)
	// 	controller.futureMessageService = [update: { Long id,
	// 		Map fInfo, String timezone ->
	// 		new Result(success:true, payload:fMsg1)
	// 	}] as FutureMessageService

	// 	when:
	// 	request.json = "{'future-message':{}}"
	// 	request.method = "PUT"
	// 	params.id = fMsg1.id
	// 	controller.update()

	// 	then:
	// 	response.status == HttpServletResponse.SC_OK
	// 	response.json.id == fMsg1.id
	// }

 //    // Delete
 //    // ------

 //    void "test delete nonexistent message"() {
 //    	given:
	// 	mockAuth(false, false)

	// 	when:
	// 	request.method = "DELETE"
	// 	params.id = fMsg1.id
	// 	controller.delete()

	// 	then:
	// 	response.status == HttpServletResponse.SC_NOT_FOUND
	// }
	// void "test delete forbidden message"() {
	// 	given:
	// 	mockAuth(true, false)

	// 	when:
	// 	request.method = "DELETE"
	// 	params.id = fMsg1.id
	// 	controller.delete()

	// 	then:
	// 	response.status == HttpServletResponse.SC_FORBIDDEN
	// }
	// void "test delete message"() {
	// 	given:
	// 	mockAuth(true, true)
	// 	controller.futureMessageService = [delete: { Long id ->
	// 		new Result<Void>(payload:null, status:ResultStatus.NO_CONTENT)
	// 	}] as FutureMessageService

	// 	when:
	// 	request.method = "DELETE"
	// 	params.id = fMsg1.id
	// 	controller.delete()

	// 	then:
	// 	response.status == HttpServletResponse.SC_NO_CONTENT
	// }
}
