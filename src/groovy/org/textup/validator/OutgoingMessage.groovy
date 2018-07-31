package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.type.RecordItemType
import org.textup.type.VoiceLanguage

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class OutgoingMessage {

	String message
	MediaInfo media
	RecordItemType type = RecordItemType.TEXT
	VoiceLanguage language = VoiceLanguage.ENGLISH

	Recipients<Long, Contact> contacts
	Recipients<Long, SharedContact> sharedContacts
	Recipients<Long, ContactTag> tags

	static constraints = {
		// [SHARED maxSize] 65535 bytes max for `text` column divided by 4 bytes per character ut8mb4
		message blank: true, nullable: true, maxSize:15000
		media nullable: true, validator: { MediaInfo mInfo, OutgoingMessage obj ->
			// message must have at least one of text and media
			if ((!mInfo || mInfo.isEmpty()) && !obj.message) { ["noInfo"] }
		}
	}

	// Methods
	// -------

	HashSet<Contactable> toRecipients() {
		HashSet<Contactable> recipients = new HashSet<>()
        // add all contactables to a hashset to avoid duplication
        recipients.addAll(contacts.recipients)
        recipients.addAll(sharedContacts.recipients)
        tags.recipients.each { ContactTag ct1 -> recipients.addAll(ct1.members ?: []) }
        recipients
	}

	// Property Access
	// ---------------

	// called during validation so needs to null-safe
	String getName() {
		if (!contacts || !tags) {
			return ""
		}
		Long id = contacts.recipients?.find { Contact c1 -> c1.id }?.id
		if (id) { // don't return contact name, instead id, for PHI
			Helpers.messageSource.getMessage("outgoingMessage.getName.contactId",
            	[id] as Object[], LCH.getLocale()) ?: ""
		}
		else { tags.recipients?.find { ContactTag ct1 -> ct1.name }?.name ?: "" }
    }

	boolean getIsText() { type == RecordItemType.TEXT }

	HashSet<Phone> getPhones() {
		HashSet<Phone> phones = new HashSet<>()
		if (!contacts || !sharedContacts || !tags) {
			return phones
		}
		phones.addAll(contacts.recipients*.phone)
		phones.addAll(sharedContacts.recipients*.sharedBy)
		phones.addAll(sharedContacts.recipients*.sharedWith)
		phones.addAll(tags.recipients*.phone)
		phones
	}
}
