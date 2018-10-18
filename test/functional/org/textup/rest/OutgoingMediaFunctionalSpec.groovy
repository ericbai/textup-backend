package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import static org.springframework.http.HttpStatus.*

class OutgoingMediaFunctionalSpec extends RestSpec {

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    void "test scheduling a future message call with media"() {
        given:
        String authToken = getAuthToken()
        String thisMessage = TestHelpers.randString()
        String thisData = TestHelpers.encodeBase64String(TestHelpers.getJpegSampleData256())
        String thisChecksum = TestHelpers.getChecksum(thisData)
        Long cId = remote.exec({ un ->
            Phone p1 = Staff.findByUsername(un).phone
            return Contact.findAllByPhone(p1)[0].id
        }.curry(loggedInUsername))
        List mediaActions = []
        2.times {
            mediaActions << [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: MediaType.IMAGE_JPEG.mimeType,
                data: thisData,
                checksum: thisChecksum
            ]
        }

        when:
        Map beforeCounts = remote.exec({
            [
                numFuture: FutureMessage.count(),
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })
        RestResponse response = rest.post("${baseUrl}/v1/future-messages?contactId=${cId}") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                'future-message' {
                    message = thisMessage
                    type = FutureMessageType.CALL.toString().toLowerCase()
                    startDate = DateTime.now().minusDays(2).toString()
                    doMediaActions = mediaActions
                }
            }
        }
        Map afterCounts = remote.exec({
            [
                numFuture: FutureMessage.count(),
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })

        then: "future message created but not sent out yet!"
        response.status == CREATED.value()
        response.json['future-message'] instanceof Map
        response.json['future-message'].message == thisMessage
        (response.json['future-message'].uploadErrors instanceof List) == false
        response.json['future-message'].media?.images instanceof List
        response.json['future-message'].media?.images.size() == mediaActions.size()
        response.json['future-message'].media?.images.each {
            assert it.versions.every {
                it.type == MediaType.IMAGE_JPEG.mimeType && it.link instanceof String
            }
        }
        response.json['future-message'].media?.audio.isEmpty()
        afterCounts.numItems == beforeCounts.numItems
        afterCounts.numReceipts == beforeCounts.numReceipts
        afterCounts.numMediaInfo == beforeCounts.numMediaInfo + 1
        afterCounts.numFuture == beforeCounts.numFuture + 1
    }

    void "test sending outgoing media via a text message"() {
        given:
        String authToken = getAuthToken()
        String thisMessage = TestHelpers.randString()
        String thisData = TestHelpers.encodeBase64String(TestHelpers.getJpegSampleData256())
        String thisChecksum = TestHelpers.getChecksum(thisData)
        Long cId = remote.exec({ un ->
            Phone p1 = Staff.findByUsername(un).phone
            return Contact.findAllByPhone(p1)[0].id
        }.curry(loggedInUsername))
        List mediaActions = []
        (Constants.MAX_NUM_MEDIA_PER_MESSAGE * 2).times {
            mediaActions << [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: MediaType.IMAGE_JPEG.mimeType,
                data: thisData,
                checksum: thisChecksum
            ]
        }

        when:
        Map beforeCounts = remote.exec({
            [
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })
        RestResponse response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer ${authToken}")
            json {
                record {
                    sendToContacts = [cId]
                    contents = thisMessage
                    doMediaActions = mediaActions
                }
            }
        }
        Map afterCounts = remote.exec({
            [
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })

        then:
        response.status == CREATED.value()
        response.json.records instanceof List
        response.json.records.size() == 1
        response.json.records[0].contents == thisMessage
        (response.json.records[0].uploadErrors instanceof List) == false
        response.json.records[0].media?.images instanceof List
        response.json.records[0].media?.images.size() == mediaActions.size()
        response.json.records[0].media?.images.each {
            assert it.versions.every {
                it.type == MediaType.IMAGE_JPEG.mimeType && it.link instanceof String
            }
        }
        response.json.records[0].media?.audio.isEmpty()
        afterCounts.numItems == beforeCounts.numItems + 1
        afterCounts.numReceipts == beforeCounts.numReceipts +
            Math.ceil(mediaActions.size() / Constants.MAX_NUM_MEDIA_PER_MESSAGE)
        afterCounts.numMediaInfo == beforeCounts.numMediaInfo + 1
    }

    void "test upload outgoing media with some upload errors"() {
        given: "re-override storage service to have some upload errors"
        String errorMsg = TestHelpers.randString()
        remote.exec({ thisError ->
            TestHelpers.forceMock(ctx.storageService, "uploadAsync") {
                Result failRes = new Result(status: ResultStatus.BAD_REQUEST,
                    errorMessages: [thisError])
                new ResultGroup([failRes])
            }
            return
        }.curry(errorMsg))

        and: "set up other necessary info to create outgoing message"
        String authToken = getAuthToken()
        String thisMessage = TestHelpers.randString()
        String thisData = TestHelpers.encodeBase64String(TestHelpers.getJpegSampleData256())
        String thisChecksum = TestHelpers.getChecksum(thisData)
        Long cId = remote.exec({ un ->
            Phone p1 = Staff.findByUsername(un).phone
            return Contact.findAllByPhone(p1)[0].id
        }.curry(loggedInUsername))
        List mediaActions = []
        2.times {
            mediaActions << [
                action: Constants.MEDIA_ACTION_ADD,
                mimeType: MediaType.IMAGE_JPEG.mimeType,
                data: thisData,
                checksum: thisChecksum
            ]
        }

        when:
        Map beforeCounts = remote.exec({
            [
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })
        RestResponse response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                record {
                    sendToContacts = [cId]
                    contents = thisMessage
                    doMediaActions = mediaActions
                }
            }
        }
        Map afterCounts = remote.exec({
            [
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })

        then: "media is created and message is sent normally, but upload errors are noted"
        response.status == CREATED.value()
        response.json.records instanceof List
        response.json.records.size() == 1
        response.json.records[0].contents == thisMessage
        response.json.records[0].media.uploadErrors instanceof List
        response.json.records[0].media.uploadErrors.contains(errorMsg)
        response.json.records[0].media.images instanceof List
        response.json.records[0].media.images.size() == mediaActions.size()
        response.json.records[0].media.images.each {
            assert it.versions.every {
                it.type == MediaType.IMAGE_JPEG.mimeType && it.link instanceof String
            }
        }
        response.json.records[0].media.audio.isEmpty()
        afterCounts.numItems == beforeCounts.numItems + 1
        afterCounts.numReceipts == beforeCounts.numReceipts +
            Math.ceil(mediaActions.size() / Constants.MAX_NUM_MEDIA_PER_MESSAGE)
        afterCounts.numMediaInfo == beforeCounts.numMediaInfo + 1
    }
}
