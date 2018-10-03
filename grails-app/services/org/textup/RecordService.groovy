package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.*
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class RecordService {

    AuthService authService
    GrailsApplication grailsApplication
    MediaService mediaService
    OutgoingMessageService outgoingMessageService
    ResultFactory resultFactory
    StorageService storageService

    // Create
    // ------

    ResultGroup<RecordItem> create(Long phoneId, Map body) {
        Phone p1 = Phone.get(phoneId)
        if (!p1) {
            return resultFactory.failWithCodeAndStatus("recordService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY).toGroup()
        }
        // TODO
        Staff authUser = authService.loggedInAndActive
        if (p1.owner.all.any { Staff s2 -> s2.id == authUser.id }) {
            return resultFactory.failWithCodeAndStatus("phone.notOwner", ResultStatus.FORBIDDEN).toGroup()
        }
        Result<Class<RecordItem>> res = determineClass(body)
        if (!res.success) { return res.toGroup() }
        switch(res.payload) {
            case RecordText: createText(p1, body); break;
            case RecordCall: createCall(p1, body).toGroup(); break;
            default: createNote(p1, body).toGroup() // RecordNote
        }
    }

    // Don't roll back because this creates a ResultGroup of many individual Results.
    // We don't want to throw away the results that actually successfuly completed
    protected ResultGroup<RecordItem> createText(Phone p1, Map body) {
        // step 1: handle media upload, storing upload errors on request
        Collection<UploadItem> itemsToUpload = []
        MediaInfo mInfo
        if (mediaService.hasMediaActions(body)) {
            Result<MediaInfo> mediaRes = mediaService.handleActions(new MediaInfo(),
                itemsToUpload.&addAll, body)
            if (mediaRes.success) {
                mInfo = mediaRes.payload
            }
            else { return mediaRes.toGroup() }
        }
        // step 2: build outgoing message and check to see if # recipients falls within bounds
        Result<OutgoingMessage> msgRes = buildOutgoingMessage(p1, body, mInfo)
            .then { OutgoingMessage msg1 -> checkOutgoingMessageRecipients(msg1) }
        if (!msgRes.success) { return msgRes.toGroup() }
        // step 3: upload assets after all validation is done
        Collection<String> errorMsgs = []
        storageService.uploadAsync(itemsToUpload)
            .failures
            .each { Result<?> failRes -> errorMsgs += failRes.errorMessages }
        Helpers.trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, errorMsgs)
            .logFail("RecordService.createText")
        // step 4: actually send outgoing message
        outgoingMessageService.sendMessage(p1, msgRes.payload, mInfo, authService.loggedInAndActive)
    }
    protected Result<OutgoingMessage> buildOutgoingMessage(Phone p1, Map body, MediaInfo mInfo = null) {
        // step 1: create each type of recipient
        ContactRecipients contacts = new ContactRecipients(phone: p1,
            ids: Helpers.allTo(Long, Helpers.to(List, body.sendToContacts)))
        SharedContactRecipients sharedContacts = new SharedContactRecipients(phone: p1,
            ids: Helpers.allTo(Long, Helpers.to(List, body.sendToSharedContacts)))
        ContactTagRecipients tags = new ContactTagRecipients(phone: p1,
            ids: Helpers.allTo(Long, Helpers.to(List, body.sendToTags)))
        NumberToContactRecipients numToContacts = new NumberToContactRecipients(phone: p1,
            ids: Helpers.allTo(String, Helpers.to(List, body.sendToPhoneNumbers)))
        ResultGroup<RecordItem> resGroup = new ResultGroup<>()
        [contacts, sharedContacts, tags, numToContacts].each { Recipients<?,?> recips ->
            if (!recips.validate()) {
                resGroup << resultFactory.failWithValidationErrors(recips.errors)
            }
        }
        if (resGroup.anyFailures) {
            return resultFactory.failWithResultsAndStatus(resGroup.failures,
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        // step 2: build outgoing msg
        OutgoingMessage msg1 = new OutgoingMessage(message: body.contents as String,
            media: mInfo,
            contacts: contacts.mergeRecipients(numToContacts),
            sharedContacts: sharedContacts,
            tags: tags)
        if (msg1.validate()) {
            resultFactory.success(msg1)
        }
        else { resultFactory.failWithValidationErrors(msg1.errors) }
    }
    protected Result<OutgoingMessage> checkOutgoingMessageRecipients(OutgoingMessage msg1) {
        // step 1: validate number of recipients
        HashSet<Contactable> recipients = msg1.toRecipients()
        Integer maxNumRecipients = Helpers.to(Integer, grailsApplication.flatConfig["textup.maxNumText"])
        if (recipients.size() > maxNumRecipients) {
            return resultFactory.failWithCodeAndStatus("recordService.create.tooManyForText",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        if (recipients.isEmpty()) {
            return resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
        resultFactory.success(msg1)
    }

    @RollbackOnResultFailure
    protected Result<RecordItem> createCall(Phone p1, Map body) {
        // step 1: create and validate recipients
        Recipients<Long, ? extends Contactable> recips
        if (body.callContact) {
            recips = new ContactRecipients(phone: p1, ids: [Helpers.to(Long, body.callContact)])
        }
        else { // body.callSharedContact
            recips = new SharedContactRecipients(phone: p1, ids: [Helpers.to(Long, body.callSharedContact)])
        }
        if (!recips.validate()) {
            return resultFactory.failWithValidationErrors(recips.errors)
        }
        // step 2: ensure that we have at least one contactable to send to.
        // That is, ensure that the provided id actually resolved to a contactable as the check
        // in the RecordController only checks for the form of the body
        Contactable cont1 = recips.recipients[0] as Contactable
        if (!cont1) {
            return resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
        outgoingMessageService.startBridgeCall(p1, cont1, authService.loggedInAndActive)
    }

    @RollbackOnResultFailure
    protected Result<RecordItem> createNote(Phone p1, Map body) {
        checkNoteTarget(p1, body)
            .then { Record rec1 -> mergeNote(new RecordNote(record: rec1), body) }
            .then { RecordNote note1 -> resultFactory.success(note1, ResultStatus.CREATED) }
    }
    protected Result<Record> checkNoteTarget(Phone p1, Map body) {
        // step 1: create and validate recipients
        Recipients<Long, ? extends WithRecord> recips
        if (body.forSharedContact) {
            recips = new SharedContactRecipients(phone: p1, ids: [Helpers.to(Long, body.forSharedContact)])
        }
        else if (body.forContact) {
            recips = new ContactRecipients(phone: p1, ids: [Helpers.to(Long, body.forContact)])
        }
        else { // body.forTag
            recips = new ContactTagRecipients(phone: p1, ids: [Helpers.to(Long, body.forTag)])
        }
        if (!recips.validate()) {
            return resultFactory.failWithValidationErrors(recips.errors)
        }
        // step 2: ensure that we have at least one entity to add the note to
        // That is, ensure that the provided id actually resolved to a entity as the check
        // in the RecordController only checks for the form of the body
        WithRecord with1 = recips.recipients[0]
        if (!with1) {
            return resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
        with1.tryGetRecord()
    }
    protected Result<RecordNote> mergeNote(RecordNote note1, Map body) {
        // step 1: handle media actions
        Collection<UploadItem> itemsToUpload = []
        if (mediaService.hasMediaActions(body)) {
            Result<MediaInfo> mediaRes = mediaService.handleActions(
                note1.media ?: new MediaInfo(),
                itemsToUpload.&addAll, body)
            if (mediaRes.success) {
                note1.media = mediaRes.payload
            }
            else { return mediaRes }
        }
        // step 2: create validator object for note
        TempRecordNote tempNote = new TempRecordNote(info: body,
            note: note1,
            after: body.after ? Helpers.toDateTimeWithZone(body.after) : null)
        tempNote.toNote(authService.loggedInAndActive.toAuthor())
            .then { RecordNote note2 -> note2.tryCreateRevision() }
            .then { RecordNote note2 ->
                Collection<String> errorMsgs = []
                storageService.uploadAsync(itemsToUpload)
                    .failures
                    .each { Result<?> failRes -> errorMsgs += failRes.errorMessages }
                Helpers.trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, errorMsgs)
                    .logFail("RecordService.mergeNote")
                resultFactory.success(note2)
            }
    }

    // Update note
    // -----------

    @RollbackOnResultFailure
    Result<RecordItem> update(Long noteId, Map body) {
        RecordNote note1 = RecordNote.get(noteId)
        if (!note1) {
            return resultFactory.failWithCodeAndStatus("recordService.update.notFound",
                ResultStatus.NOT_FOUND, [noteId])
        }
        if (note1.isReadOnly) {
            return resultFactory.failWithCodeAndStatus("recordService.update.readOnly",
                ResultStatus.FORBIDDEN, [noteId])
        }
        mergeNote(note1, body)
    }

    // Delete note
    // -----------

    @RollbackOnResultFailure
    Result<Void> delete(Long noteId) {
        RecordNote note1 = RecordNote.get(noteId)
        if (note1) {
            if (note1.isReadOnly) {
                return resultFactory.failWithCodeAndStatus("recordService.delete.readOnly",
                    ResultStatus.FORBIDDEN, [noteId])
            }
            note1.isDeleted = true
            if (note1.save()) {
                resultFactory.success()
            }
            else { resultFactory.failWithValidationErrors(note1.errors) }
        }
        else {
            resultFactory.failWithCodeAndStatus("recordService.delete.notFound",
                ResultStatus.NOT_FOUND, [noteId])
        }
    }

    // Identification
    // --------------

    List<Class<? extends RecordItem>> parseTypes(Collection<?> rawTypes) {
        if (!rawTypes) {
            return []
        }
        HashSet<Class<? extends RecordItem>> types = new HashSet<>()
        rawTypes.each { Object obj ->
            switch (obj as String) {
                case "text": types << RecordText; break;
                case "call": types << RecordCall; break;
                case "note": types << RecordNote
            }
        }
        new ArrayList<Class<? extends RecordItem>>(types)
    }

    Result<Class<RecordItem>> determineClass(Map body) {
        if (body.callContact || body.callSharedContact) {
            resultFactory.success(RecordCall)
        }
        else if (body.sendToPhoneNumbers || body.sendToContacts ||
            body.sendToSharedContacts || body.sendToTags) {
            resultFactory.success(RecordText)
        }
        else if (body.forContact || body.forSharedContact || body.forTag) {
            resultFactory.success(RecordNote)
        }
        else {
            resultFactory.failWithCodeAndStatus("recordService.create.unknownType",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }
}
