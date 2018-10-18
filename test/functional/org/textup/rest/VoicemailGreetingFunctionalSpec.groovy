package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.media.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import static org.springframework.http.HttpStatus.*

class VoicemailGreetingFunctionalSpec extends RestSpec {

    def setup() {
        setupData()
        remote.exec({ mockedMethodsKey ->
            app.config[mockedMethodsKey] << TestHelpers.mock(MediaPostProcessor, "process") {
                UploadItem uItem1 = TestHelpers.buildUploadItem(MediaType.AUDIO_MP3)
                UploadItem uItem2 = TestHelpers.buildUploadItem(MediaType.AUDIO_WEBM_OPUS)
                ctx.resultFactory.success(uItem1, [uItem2])
            }
            app.config[mockedMethodsKey] << TestHelpers.mock(ctx.callService, "interrupt") {
                ctx.resultFactory.success()
            }
            return
        }.curry(MOCKED_METHODS_CONFIG_KEY))
    }

    def cleanup() {
        cleanupData()
    }

    void "test recording and reviewing new voicemail greeting over the phone"() {
        given:
        String callId = TestHelpers.randString()
        String fromNum = TestHelpers.randPhoneNumber()
        String phoneNum = remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
            assert s1.personalPhoneNumber.e164PhoneNumber != fNum
            return s1.phone.number.e164PhoneNumber
        }.curry(loggedInUsername, fromNum))

        when: "recording a voicemail greeting over the phone"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", callId)
        form.add("From", fromNum)
        form.add("To", phoneNum)
        RestResponse response = rest.post("${baseUrl}/v1/public/records?handle=${CallResponse.VOICEMAIL_GREETING_RECORD}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Record.@action.toString().contains(CallResponse.VOICEMAIL_GREETING_PROCESSING.toString())
        response.xml.Record.@recordingStatusCallback.toString().contains(CallResponse.VOICEMAIL_GREETING_PROCESSED.toString())

        when: "processing"
        response = rest.post("${baseUrl}/v1/public/records?handle=${CallResponse.VOICEMAIL_GREETING_PROCESSING}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Play[0].text() == Constants.CALL_HOLD_MUSIC_URL
        response.xml.Play[1].@loop.toString().isInteger() // hold music loops

        when: "done processing"
        form.set("RecordingDuration", "1234")
        form.set("RecordingUrl", "http://www.example.com")
        form.set("RecordingSid", TestHelpers.randString())
        response = rest.post("${baseUrl}/v1/public/records?handle=${CallResponse.VOICEMAIL_GREETING_PROCESSED}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "attempt to interrupt ongoing call"
        response.status == OK.value()
        response.xml != null
        response.xml.text() == ""

        when: "playing just processed voicemail greeting"
        form = new LinkedMultiValueMap<>()
        form.add("CallSid", callId)
        form.add("From", fromNum)
        form.add("To", phoneNum)
        response = rest.post("${baseUrl}/v1/public/records?handle=${CallResponse.VOICEMAIL_GREETING_PLAY}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Gather.@action.toString().contains(CallResponse.VOICEMAIL_GREETING_RECORD.toString())
        response.xml.Gather.Play[0].text() == remote.exec({ un ->
            Staff.findByUsername(un).phone.voicemailGreetingUrl.toString()
        }.curry(loggedInUsername))
    }
}
