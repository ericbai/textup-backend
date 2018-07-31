package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.nio.charset.StandardCharsets
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.type.ReceiptStatus
import org.textup.validator.PhoneNumber
import org.textup.validator.UploadItem
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordNote,
	RecordNoteRevision, RecordItemReceipt, Location, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordNoteRevisionSpec extends Specification {

    void "test validation"() {
        given:
        Record rec = new Record()
        rec.save(flush: true, failOnError: true)

        when: "empty revision"
        RecordNoteRevision rev1 = new RecordNoteRevision()

        then: "requires an associated note"
        rev1.validate() == false
        rev1.errors.errorCount == 2
        rev1.errors.getFieldErrorCount('whenChanged') == 1
        rev1.errors.getFieldErrorCount('note') == 1

        when: "associate revision with a note"
        RecordNote note1 = new RecordNote(record:rec)
        assert note1.validate()
        rev1.note = note1
        rev1.whenChanged = note1.whenChanged

        then:
        rev1.validate() == true

        when: "noteContents too long"
        rev1.noteContents = buildVeryLongString()

        then:
        rev1.validate() == false
        rev1.errors.errorCount == 1
        rev1.errors.getFieldErrorCount("noteContents") == 1
    }

    // Helpers
    // -------

    protected String buildVeryLongString() {
        StringBuilder sBuilder = new StringBuilder()
        15000.times { it -> sBuilder << it }
        sBuilder.toString()
    }
}
