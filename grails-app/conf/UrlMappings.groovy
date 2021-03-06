import org.textup.*
import org.textup.rest.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

class UrlMappings {

	static mappings = {
        "/$controller/$action?/$id?(.$format)?"()

        //login endpoint is `/login` (provided by Spring Security REST)
        "/"(redirect: [controller: "super", permanent: true])
        "500"(view: "/error")

        group("/v1") {

        	// Public
	        // ------

            "/public/notifications"(resources: "notify")
            "/public/organizations"(resources: "organization")
            "/public/records"(resources: "publicRecord")
            "/public/reset"(resources: "passwordReset")
            "/public/staff"(resources: "staff")

            // Restricted utilities
            // --------------------

            "/duplicates"(resources: "duplicate")
            "/numbers"(resources: "number")
            "/sockets"(resources: "socket")
            "/validate"(resources: "validate")

            // Restricted
            // ----------

            "/announcements"(resources: RestUtils.RESOURCE_ANNOUNCEMENT)
            "/contacts"(resources: RestUtils.RESOURCE_CONTACT)
            "/future-messages"(resources: RestUtils.RESOURCE_FUTURE_MESSAGE)
            "/organizations"(resources: RestUtils.RESOURCE_ORGANIZATION)
            "/records"(resources: RestUtils.RESOURCE_RECORD_ITEM)
            "/sessions"(resources: RestUtils.RESOURCE_SESSION)
            "/staff"(resources: RestUtils.RESOURCE_STAFF)
            "/tags"(resources: RestUtils.RESOURCE_TAG)
            "/teams"(resources: RestUtils.RESOURCE_TEAM)
        }
	}
}
