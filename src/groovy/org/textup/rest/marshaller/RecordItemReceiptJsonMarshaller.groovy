package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class RecordItemReceiptJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { String namespace,
		SpringSecurityService springSecurityService, AuthService authService,
		LinkGenerator linkGenerator, ReadOnlyRecordItemReceipt receipt ->
		[
			id: receipt.id,
			status: receipt.status.toString(),
			receivedBy: receipt.receivedBy.e164PhoneNumber
		]
	}

	RecordItemReceiptJsonMarshaller() {
		super(ReadOnlyRecordItemReceipt, marshalClosure)
	}
}
