import groovy.sql.*
import org.textup.*
import org.textup.type.*

databaseChangeLog = {

    // need to drop foreign key constraint on `contact_number.owner_id` so that we can update
    // the `owner_id` from `contact` ids to `phone_record` ids
    changeSet(author: "ericbai (generated)", id: "1551375338796-26") {
        dropForeignKeyConstraint(baseTableName: "contact_number", baseTableSchemaName: "prodDb", constraintName: "FK_1cjhdw1dw396gqxyeqbv7x863")
    }

    changeSet(author: "ericbai", id: "1551375338796-custom-12") {
        grailsChange {
            change {
                Map tagOldIdToNewMembersId = [:]
                Map contactOldIdToNewId = [:]
                Map contactOldIdToRecordId = [:]
                // step 1: create tags withOUT members
                sql.rows("SELECT * FROM contact_tag").each { GroovyRowResult oldTag ->
                    tagOldIdToNewMembersId[oldTag.id] = migrateTag(sql, oldTag)
                }
                // step 2: create contacts withOUT numbers
                sql.rows("SELECT * FROM contact").each { GroovyRowResult oldContact ->
                    contactOldIdToRecordId[oldContact.id] = oldContact.record_id
                    contactOldIdToNewId[oldContact.id] = migrateContact(sql, oldContact)
                }
                // step 3: add numbers to contacts
                sql.rows("SELECT * FROM contact_number").each { GroovyRowResult oldNumber ->
                    updateContactNumber(sql, contactOldIdToNewId, oldNumber)
                }
                // step 4: add contacts to tags
                sql.rows("SELECT * FROM contact_tag_contact").each { GroovyRowResult oldMembership ->
                    migrateTagMembership(sql,
                        tagOldIdToNewMembersId[oldMembership.contact_tag_members_id],
                        contactOldIdToNewId[oldMembership.contact_id])
                }
                // step 5: create shared contacts
                sql.rows("SELECT * FROM shared_contact").each { GroovyRowResult oldSharedContact ->
                    migrateSharedContact(sql, contactOldIdToNewId, contactOldIdToRecordId, oldSharedContact)
                }
            }
            // To avoid writing ultimately never-used code, we will add the rollback code only if needed
        }
    }

    // after all `owner_id` values have been updated to be `phone_record` ids, re-add foreign key constraint
    changeSet(author: "ericbai (generated)", id: "1551375338796-39") {
        addForeignKeyConstraint(baseColumnNames: "owner_id", baseTableName: "contact_number", constraintName: "FK_1cjhdw1dw396gqxyeqbv7x863", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_record", referencesUniqueColumn: "false")
    }
}

Long migrateTag(Sql sql, GroovyRowResult oldTag) {
    Long prmId = sql.executeInsert([version: 0], """
        INSERT INTO phone_record_members (version)
        VALUES (:version)
    """)[0][0]
    sql.executeInsert([
        class: GroupPhoneRecord.class.canonicalName,
        version: oldTag.version,
        lastTouched: "2019-03-01 00:00:00",
        whenCreated: oldTag.when_created,
        phoneId: oldTag.phone_id,
        status: PhoneRecordStatus.ACTIVE.toString(),
        recordId: oldTag.record_id,
        isDeleted: oldTag.is_deleted,
        membersId: prmId,
        hexColor: oldTag.hex_color,
        name: oldTag.name
    ], """
        INSERT INTO phone_record (class,
            version,
            last_touched,
            when_created,
            phone_id,
            status,
            record_id,
            is_deleted,
            members_id,
            group_hex_color,
            name)
        VALUES (:class,
            :version,
            :lastTouched,
            :whenCreated,
            :phoneId,
            :status,
            :recordId,
            :isDeleted,
            :membersId,
            :hexColor,
            :name)
    """)
    prmId
}

Long migrateContact(Sql sql, GroovyRowResult oldContact) {
    sql.executeInsert([
        class: IndividualPhoneRecord.class.canonicalName,
        version: oldContact.version,
        name: oldContact.name,
        individualNote: oldContact.note,
        phoneId: oldContact.phone_id,
        recordId: oldContact.record_id,
        status: oldContact.status,
        isDeleted: oldContact.is_deleted,
        whenCreated: oldContact.when_created,
        lastTouched: oldContact.last_touched
    ], """
        INSERT INTO phone_record (class,
            version,
            name,
            individual_note,
            phone_id,
            record_id,
            status,
            is_deleted,
            when_created,
            last_touched)
        VALUES (:class,
            :version,
            :name,
            :individualNote,
            :phoneId,
            :recordId,
            :status,
            :isDeleted,
            :whenCreated,
            :lastTouched)
    """)[0][0]
}

void updateContactNumber(Sql sql, Map contactOldIdToNewId, GroovyRowResult oldNumber) {
    sql.executeUpdate([
        ownerId: contactOldIdToNewId[oldNumber.owner_id],
        thisId: oldNumber.id
    ], """
        UPDATE contact_number
        SET owner_id = :ownerId
        WHERE id = :thisId
    """)
}

void migrateTagMembership(Sql sql, Long prmId, Long prId) {
    sql.executeInsert([version: 0, membersId: prmId, phoneRecordId: prId], """
        INSERT INTO phone_record_members_phone_record (phone_record_members_phone_records_id,
            phone_record_id)
        VALUES (:membersId,
            :phoneRecordId)
    """)
}

void migrateSharedContact(Sql sql, Map contactOldIdToNewId, Map contactOldIdToRecordId,
    GroovyRowResult oldSharedContact) {

    sql.executeInsert([
        class: PhoneRecord.class.canonicalName,
        version: oldSharedContact.version,
        shareSourceId: contactOldIdToNewId[oldSharedContact.contact_id],
        dateExpired: oldSharedContact.date_expired,
        permission: oldSharedContact.permission,
        phoneId: oldSharedContact.shared_with_id,
        recordId: contactOldIdToRecordId[oldSharedContact.contact_id],
        whenCreated: oldSharedContact.when_created,
        status: oldSharedContact.status,
        lastTouched: oldSharedContact.last_touched,
        isDeleted: 0
    ], """
        INSERT INTO phone_record (class,
            version,
            share_source_id,
            date_expired,
            permission,
            phone_id,
            record_id,
            when_created,
            status,
            last_touched,
            is_deleted)
        VALUES (:class,
            :version,
            :shareSourceId,
            :dateExpired,
            :permission,
            :phoneId,
            :recordId,
            :whenCreated,
            :status,
            :lastTouched,
            :isDeleted)
    """)
}
