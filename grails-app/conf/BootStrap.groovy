import grails.util.GrailsUtil
import org.textup.*
import org.textup.rest.*
import org.textup.util.*

class BootStrap {

	def marshallerInitializerService

    def init = { servletContext ->
    	//marshaller and renderer customization from http://groovyc.net/non-trivial-restful-apis-in-grails-part-1/
    	marshallerInitializerService.initialize()

    	if (GrailsUtil.environment == "development" || GrailsUtil.environment == "production") {
    		//Define security roles
	        Role adminRole = Role.findByAuthority("ROLE_ADMIN") ?: new Role(authority:"ROLE_ADMIN").save(flush:true)
	        Role userRole = Role.findByAuthority("ROLE_USER") ?: new Role(authority:"ROLE_USER").save(flush:true)

	    	if (Organization.count() == 0) {
	    		//create an unverified org
	    		Organization org1 = new Organization(name:"Shelter Rhode Island", status: Constants.ORG_PENDING)
		    	org1.location = new Location(address:"30 Howard Ave, North Providence, Rhode Island 02911, United States", lat:41.858923G, lon:-71.473439G)
		    	org1.save(flush:true)
		    	Staff pendingOrgStaff = new Staff(username:"demo-pending", password:"password",
		    		name:"Mallory Pending1", email:"connect@textup.org", org:org1, status:Constants.STATUS_ADMIN)
		    	pendingOrgStaff.save(flush:true, failOnError:true)
				StaffRole.create(pendingOrgStaff, userRole, true)

	    		//create our full-fleges demo org
	    		Organization org = new Organization(name:"Rhode Island House", status: Constants.ORG_APPROVED)
		    	org.location = new Location(address:"577 Cranston St", lat:41.807982G, lon:-71.435045G)
		    	org.save(flush:true)

	    		//create the super user
	    		Staff superUser = new Staff(username:"super", password:"password",
		    		name:"Super", email:"connect@textup.org", org:org, status:Constants.STATUS_ADMIN)
	    		superUser.save(flush:true, failOnError:true)
	    		StaffRole.create(superUser, adminRole, true)

		    	//create teams
		    	Team t1 = new Team(name:"Rapid Rehousing", org:org)
				Team t2 = new Team(name:"Housing First", org:org)
				t1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
				t2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
				t1.save(flush:true, failOnError:true)
				t2.save(flush:true, failOnError:true)
				//create phones for teams
				TeamPhone tPh1 = new TeamPhone()
		        tPh1.numberAsString = "1112223336"
		        t1.phone = tPh1
		        tPh1.save(flush:true, failOnError:true)

				//create staff
				Staff admin = new Staff(username:"demo-admin", password:"password",
		    		name:"Mike Staff1", email:"connect@textup.org", org:org, status:Constants.STATUS_ADMIN)
		    	Staff s1 = new Staff(username:"demo-staff1", password:"password",
		    		name:"Eva Staff2", email:"connect@textup.org", org:org, status:Constants.STATUS_STAFF)
				Staff s2 = new Staff(username:"demo-staff2", password:"password",
					name:"Johnny Staff3", email:"connect@textup.org", org:org, status:Constants.STATUS_PENDING)
		    	admin.personalPhoneNumberAsString = "267 888 7452"
		    	s1.personalPhoneNumberAsString = "267 888 7452"
		    	s2.personalPhoneNumberAsString = "267 888 7452"
		    	admin.save(flush:true, failOnError:true)
				s1.save(flush:true, failOnError:true)
				s2.save(flush:true, failOnError:true)
				//set availability
				admin.schedule.updateWithIntervalStrings(wednesday:["0100:2300"])
				s1.schedule.updateWithIntervalStrings(wednesday:["0100:2300"])
				s2.schedule.updateWithIntervalStrings(wednesday:["0100:2300"])
				//create staff phones
		    	StaffPhone p1 = new StaffPhone()
		    	StaffPhone p2 = new StaffPhone()
		    	StaffPhone p3 = new StaffPhone()
		    	p1.numberAsString = "401 234 4510"
		    	admin.phone = p1
		    	p1.save(flush:true, failOnError:true)
		    	p2.numberAsString = "401 234 0315"
		    	s1.phone = p2
		    	p2.save(flush:true, failOnError:true)
		    	p3.numberAsString = "1112223335"
		    	s2.phone = p3
		    	p3.save(flush:true, failOnError:true)
		    	// create roles for staff
		    	StaffRole.create(admin, userRole, true)
		    	StaffRole.create(s1, userRole, true)
		    	StaffRole.create(s2, userRole, true)
		    	//add staff to teams
		    	(new TeamMembership(staff:admin, team:t1)).save(flush:true, failOnError:true)
		    	(new TeamMembership(staff:s1, team:t1)).save(flush:true, failOnError:true)
		    	(new TeamMembership(staff:s1, team:t2)).save(flush:true, failOnError:true)
		    	(new TeamMembership(staff:s2, team:t2)).save(flush:true, failOnError:true)

		    	//create contacts with items
		    	Contact c1 = p1.createContact([name:'John Smith'], ["626 202 7548"]).payload
				Contact c2 = p2.createContact([:], ["626 202 7548"]).payload
				Contact tC1 = tPh1.createContact([:], ["626 202 7548"]).payload
				[c1, c2, tC1]*.save(flush:true, failOnError:true)
				RecordText rText1 = c1.record.addText([contents:"Hi! Hope you're doing well today."], null).payload
		        RecordText rText2 = c2.record.addText([contents:"Hi! Hope you're doing well today."], null).payload
		        RecordText rTeText1 = tC1.record.addText([contents:"Hi! Hope you're doing well today."], null).payload
				[rText1, rText2, rTeText1]*.save(flush:true, failOnError:true)

				//share contacts
				SharedContact sc1 = p1.shareContact(c1, p2, Constants.SHARED_DELEGATE).payload
		        SharedContact sc2 = p2.shareContact(c2, p1, Constants.SHARED_DELEGATE).payload
		        [sc1, sc2]*.save(flush:true, failOnError:true)

		    	//create tags
		    	ContactTag tag1 = p1.createTag(name:"Case Management").payload
				ContactTag tag2 = p2.createTag(name:"Case Management").payload
    		    ContactTag teTag1 = tPh1.createTag(name:"Case Management").payload
        		[tag1, tag2, teTag1]*.save(flush:true, failOnError:true)
		    	//create items for team tags
		    	teTag1.record.addText([contents:"Hi! Hope you're doing well today."], null).payload
		    		.save(flush:true, failOnError:true)
		    	//tag memberships
		    	(new TagMembership(tag:tag1, contact:c1)).save(flush:true, failOnError:true)
		    }
    	}
    }
    def destroy = {
    }
}
