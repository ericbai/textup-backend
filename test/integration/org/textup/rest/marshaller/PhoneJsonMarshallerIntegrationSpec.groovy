package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.Unroll

class PhoneJsonMarshallerIntegrationSpec extends CustomSpec {

    def _originalGetLoggedInAndActive

    def grailsApplication
    AuthService authService

    def setup() {
        setupIntegrationData()
        authService = grailsApplication.mainContext.getBean('authService')
    }

    def cleanup() {
        cleanupIntegrationData()
        // restore overridden methods
        if (_originalGetLoggedInAndActive) {
            authService.metaClass.getLoggedInAndActive = _originalGetLoggedInAndActive
            _originalGetLoggedInAndActive = null
        }
    }

    protected void overrideGetLoggedInAndActiveWith(Closure closure) {
        if (!_originalGetLoggedInAndActive) {
            _originalGetLoggedInAndActive = authService.metaClass.getLoggedInAndActive
        }
        authService.metaClass.getLoggedInAndActive = closure
    }

    @Unroll
    void "test marshal phone when has notification policy and is #description"() {
        given:
        Staff authUser
        if (isLoggedIn) {
            if (isOwner) {
                authUser = p1.owner.all[0]
            }
            else {
                authUser = s2
                assert p1.owner.all.contains(s2) == false
            }

            NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(authUser.id)
            np1.useStaffAvailability = true
            np1.save(flush:true, failOnError:true)
        }
        overrideGetLoggedInAndActiveWith({ authUser })

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(p1 as JSON)
        }

        then:
        json.id == p1.id
        json.number == p1.number.e164PhoneNumber
        json.awayMessage == p1.awayMessage
        json.tags.size() == p1.tags.size()
        json.voice == p1.voice.toString()
        json.language == p1.language.toString()
        json.mandatoryEmergencyMessage == Constants.AWAY_EMERGENCY_MESSAGE
        p1.tags.every { ContactTag ct1 ->
            json.tags.find { it.id == ct1.id }
        }
        if (showAvailabilityInfo) {
            NotificationPolicy np1 = p1.owner.getPolicyForStaff(authUser.id)
            assert json.availability instanceof Map
            assert json.availability.useStaffAvailability == true
            assert json.availability.manualSchedule == np1.useStaffAvailability
            assert json.availability.isAvailable == np1.useStaffAvailability
            assert json.availability.isAvailableNow == s1.isAvailableNow()
            assert json.availability.schedule == np1.schedule
            assert json.availability.schedule == null // no schedule created yet
            assert json.others instanceof Collection
            assert json.others.size() == p1.owner.all.size() - 1
        }
        else {
            assert json.availability == null
            assert json.others == null
        }

        where:
        isLoggedIn | isOwner | showAvailabilityInfo | description
        false      | false   | false                | "not logged in"
        true       | false   | false                | "logged in but not one of phone's owners"
        true       | true    | true                 | "logged in, phone owner"
    }

    void "test marshal phone when no notification policy"() {
        given: "a new staff member owner for this phone"
        Staff staff1 = new Staff(username: UUID.randomUUID().toString(),
            name: "Name",
            password: "password",
            email: "hello@its.me",
            org: org,
            manualSchedule: true,
            isAvailable: false)
        staff1.save(flush:true, failOnError:true)

        p1.updateOwner(staff1)
        p1.save(flush:true, failOnError:true)

        assert p1.owner.getPolicyForStaff(staff1.id) == null
        overrideGetLoggedInAndActiveWith({ staff1 })

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(p1 as JSON)
        }

        then: "default to staff availability and do not show policy-level availability info"
        json.id == p1.id
        json.number == p1.number.e164PhoneNumber
        json.awayMessage == p1.awayMessage
        json.tags.size() == p1.tags.size()
        json.voice == p1.voice.toString()
        json.language == p1.language.toString()
        json.mandatoryEmergencyMessage == Constants.AWAY_EMERGENCY_MESSAGE
        p1.tags.every { ContactTag ct1 ->
            json.tags.find { it.id == ct1.id }
        }
        json.availability instanceof Map
        json.availability.useStaffAvailability == true
        json.availability.isAvailableNow == staff1.isAvailableNow()
        // no policy-level availability info
        json.availability.manualSchedule == null
        json.availability.isAvailable == null
        json.availability.schedule == null
        json.others instanceof Collection
        json.others.size() == 0 // this is a personal TextUp phone
    }

    void "test marshal phone with various voicemail options"() {
        given:
        String customMsg = TestHelpers.randString()
        p1.awayMessage = customMsg
        p1.media = null
        p1.useVoicemailRecordingIfPresent = false
        p1.voice = VoiceType.FEMALE
        p1.save(flush: true, failOnError: true)

        when: "use robovoice to read away message -- no voicemail recording"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(p1 as JSON)
        }

        then:
        json.id == p1.id
        json.awayMessage.contains(customMsg)
        json.voice == VoiceType.FEMALE.toString()
        json.useVoicemailRecordingIfPresent == false
        json.voicemailRecording == null

        when: "use voicemail recording"
        MediaElement e1 = TestHelpers.buildMediaElement()
        e1.sendVersion.type = MediaType.AUDIO_MP3
        p1.media = new MediaInfo()
        p1.media.addToMediaElements(e1)
        p1.useVoicemailRecordingIfPresent = true
        p1.save(flush: true, failOnError: true)

        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(p1 as JSON)
        }

        then:
        json.id == p1.id
        json.awayMessage.contains(customMsg)
        json.voice == VoiceType.FEMALE.toString()
        json.useVoicemailRecordingIfPresent == true
        json.voicemailRecording instanceof Map
        json.voicemailRecording.uid == e1.uid
        json.voicemailRecording.versions instanceof Collection
        json.voicemailRecording.versions*.type.every { it in MediaType.AUDIO_TYPES*.mimeType }
    }
}
