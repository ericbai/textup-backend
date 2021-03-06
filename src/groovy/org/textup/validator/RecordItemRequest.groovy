package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
@Validateable
class RecordItemRequest implements CanValidate {

    static final String DEFAULT_START = "beginning"
    static final String DEFAULT_END = "end"

    final boolean groupByEntity = false
    final Collection<? extends PhoneRecordWrapper> wrappers
    final Phone mutablePhone // when shared, this is the mutable, not original, phone

    Collection<Class<? extends RecordItem>> types
    DateTime start
    DateTime end

    static constraints = {
        types nullable: true
        start nullable: true
        end nullable: true
        wrappers validator: { Collection<PhoneRecordWrapper> val, RecordItemRequest obj ->
            if (val) {
                if (val.any { !it?.permissions?.canView() }) {
                    return ["someNoPermissions", val*.id]
                }
                Collection<Long> pIds = WrapperUtils.mutablePhoneIdsIgnoreFails(val)
                if (pIds.any { Long id -> id != obj.mutablePhone?.id }) {
                    return ["foreign", obj.mutablePhone?.id, val*.id]
                }
            }
        }
    }

    static Result<RecordItemRequest> tryCreate(Phone p1, Collection<? extends PhoneRecordWrapper> thisWraps,
        boolean isGrouped) {

        Collection<PhoneRecordWrapper> wraps = thisWraps ?: new ArrayList<? extends PhoneRecordWrapper>()
        RecordItemRequest iReq1 = new RecordItemRequest(isGrouped,
            Collections.unmodifiableCollection(wraps),
            p1)
        DomainUtils.tryValidate(iReq1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    List<RecordItemRequestSection> buildSections(Map params = null) {
        // when exporting, we want the oldest records first instead of most recent first
        DetachedCriteria<RecordItem> criteria = getCriteria()
        Integer total = criteria.count() as Integer
        List<RecordItem> rItems = criteria.build(RecordItems.forSort(false))
            .list(ControllerUtils.buildPagination(params, total))
        // group by entity only makes sense if we have entities and haven't fallen back
        // to getting record items for the phone overall
        if (!wrappers.isEmpty() && groupByEntity) {
            RecordUtils.buildSectionsByEntity(rItems, wrappers)
        }
        else {
            RecordItemRequestSection section1 = RecordUtils
                .buildSingleSection(mutablePhone, rItems, wrappers)
            section1 ? [section1] : []
        }
    }

    String buildFormattedStart(Object tz = null) {
        start ?
            JodaUtils.FILE_TIMESTAMP_FORMAT.print(JodaUtils.toDateTimeWithZone(start, tz)) :
            DEFAULT_START
    }

    String buildFormattedEnd(Object tz = null) {
        end ?
            JodaUtils.FILE_TIMESTAMP_FORMAT.print(JodaUtils.toDateTimeWithZone(end, tz)) :
            DEFAULT_END
    }

    // Properties
    // ----------

    DetachedCriteria<RecordItem> getCriteria() {
        wrappers.isEmpty()
            ? RecordItems.buildForPhoneIdWithOptions(mutablePhone?.id, start, end, types)
            : RecordItems.buildForRecordIdsWithOptions(WrapperUtils.recordIdsIgnoreFails(wrappers),
                start, end, types)
    }
}
