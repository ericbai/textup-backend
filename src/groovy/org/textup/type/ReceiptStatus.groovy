package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum ReceiptStatus {
	FAILED(["failed", "undelivered"]),
	PENDING(["in-progress", "ringing", "queued", "accepted", "sending", "receiving"]),
	BUSY(["busy", "no-answer"]),
	SUCCESS(["completed", "canceled", "sent", "delivered"])

	private final Collection<String> statuses
	ReceiptStatus(Collection<String> thisStatuses) { this.statuses = thisStatuses }
	Collection<String> getStatuses() { Collections.unmodifiableCollection(this.statuses) }

	static ReceiptStatus translate(String status) {
		if (!status) {
			return
		}
		String cleanedStatus = status.toLowerCase()
		if (cleanedStatus in FAILED.statuses) {
			return FAILED
		}
		else if (cleanedStatus in PENDING.statuses) {
			return PENDING
		}
		else if (cleanedStatus in BUSY.statuses) {
			return BUSY
		}
		else if (cleanedStatus in SUCCESS.statuses) {
			return SUCCESS
		}
	}
}
