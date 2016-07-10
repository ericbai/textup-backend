package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@RestApi(name="FutureMessage",description = "Operations on messages (call or text) to be \
	send in the future for staff or teams, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class FutureMessageController extends BaseController {

	static namespace = "v1"

	// authService from superclass
	FutureMessageService futureMessageService

	// List
	// ----

	@Transactional(readOnly=true)
    def index() {
    	if (!Helpers.exactly(1, ["contactId", "tagId"], params)) {
    		badRequest()
		}
    	else if (params.long("contactId")) {
            listForContact(params)
        }
        else { listForTag(params) } // tagId
    }
    protected def listForContact(GrailsParameterMap params) {
    	Long cId = params.long("contactId")
        Contact c1 = Contact.get(cId)
        if (!c1) {
        	notFound()
        }
        Contactable cont
        if (authService.hasPermissionsForContact(cId)) {
            cont = c1 as Contactable
        }
        else {
            Long scId = authService.getSharedContactIdForContact(c1.id)
            if (scId) {
                cont = SharedContact.get(scId) as Contactable
            }
            else { forbidden(); return; }
        }
        genericListActionForClosures(FutureMessage, { Map options ->
        	cont.countFutureMessages()
    	}, { Map options ->
    		cont.getFutureMessages(options)
		}, params)
    }
    protected def listForTag(GrailsParameterMap params) {
    	Long ctId = params.long("tagId")
    	ContactTag ct1 = ContactTag.get(ctId)
    	if (!ct1) {
    		notFound()
    	}
    	if (!authService.hasPermissionsForTag(ctId)) {
    		forbidden()
    	}
    	genericListActionForClosures(FutureMessage, { Map options ->
        	ct1.record.countFutureMessages()
    	}, { Map options ->
    		ct1.record.getFutureMessages(options)
		}, params)
    }

    // Show
    // ----

    @Transactional(readOnly=true)
    def show() {
    	Long id = params.long("id")
        if (authService.exists(FutureMessage, id)) {
            if (authService.hasPermissionsForFutureMessage(id)) {
                genericShowAction(FutureMessage, id)
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    def save() {
    	if (!validateJsonRequest(request, "future-message")) { return; }
    	Map fInfo = (request.properties.JSON as Map)["future-message"] as Map
        String tz = params.timezone as String
        if (!Helpers.exactly(1, ["contactId", "tagId"], params)) {
            badRequest()
        }
        else if (params.long("contactId")) {
            Long cId = params.long("contactId")
            if (!authService.exists(Contact, cId)) {
                return notFound()
            }
            // try to create, if allowed
            if (authService.hasPermissionsForContact(cId)) {
                handleSaveResult(FutureMessage,
                    futureMessageService.createForContact(cId, fInfo, tz))
            }
            else if (authService.getSharedContactIdForContact(cId)) {
                handleSaveResult(FutureMessage,
                    futureMessageService.createForSharedContact(cId, fInfo, tz))
            }
            else { forbidden() }
        }
        else { // tag id
            Long ctId = params.long("tagId")
            if (!authService.exists(ContactTag, ctId)) {
                return notFound()
            }
            // try to create, if allowed
            if (authService.hasPermissionsForTag(ctId)) {
                handleSaveResult(FutureMessage,
                    futureMessageService.createForTag(ctId, fInfo, tz))
            }
            else { forbidden() }
        }
    }

    // Update
    // ------

    def update() {
    	if (!validateJsonRequest(request, "future-message")) { return; }
    	Long id = params.long("id")
        if (authService.exists(FutureMessage, id)) {
            if (authService.hasPermissionsForFutureMessage(id)) {
                Map fInfo = (request.properties.JSON as Map)["future-message"] as Map
                handleUpdateResult(FutureMessage,
                	futureMessageService.update(id, fInfo, params.timezone as String))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Delete
    // ------

    def delete() {
    	Long id = params.long("id")
        if (authService.exists(FutureMessage, id)) {
            if (authService.hasPermissionsForFutureMessage(id)) {
                handleDeleteResult(futureMessageService.delete(id))
            }
            else { forbidden() }
        }
        else { notFound() }
    }
}
