package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import org.hibernate.criterion.CriteriaSpecification
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class IndividualPhoneRecordWrappers {

    static Result<IndividualPhoneRecordWrapper> tryCreate(Phone p1) {
        IndividualPhoneRecord.tryCreate(p1).then { IndividualPhoneRecord ipr1 ->
            IOCUtils.resultFactory.success(ipr1.toWrapper())
        }
    }

    static Result<IndividualPhoneRecordWrapper> mustFindForId(Long iprId) {
        PhoneRecord pr1 = PhoneRecord.get(prId)
        PhoneRecordWrapper w1 = pr1?.toWrapper()
        if (w1 instanceof IndividualPhoneRecordWrapper) {
            IOCUtils.resultFactory.success(w1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("contactService.update.notFound", //TODO
                ResultStatus.NOT_FOUND, [prId])
        }
    }

    static DetachedCriteria<PhoneRecord> buildForPhoneIdWithOptions(Long phoneId,
        String query = null, Collection<PhoneRecordStatus> statuses = PhoneRecordStatus.VISIBLE_STATUSES,
        boolean onlyShared = false) {

        DetachedCriteria<PhoneRecord> query = buildBase(query, statuses)
            .build { eq("phone.id", phoneId) }
        onlyShared ? query.build { isNotNull("shareSource") } : query // inner join
    }

    static DetachedCriteria<PhoneRecord> buildForSharedByIdWithOptions(Long sharedById,
        String query = null, Collection<PhoneRecordStatus> statuses = PhoneRecordStatus.VISIBLE_STATUSES) {

        buildBase(query, statuses).build { eq("shareSource.phone.id", phoneId) } // inner join
    }

    static Closure<List<IndividualPhoneRecordWrapper>> doList(DetachedCriteria<PhoneRecord> query) {
        return { Map opts -> query.build(forSort()).list(opts)*.toWrapper() }
    }

    static Result<List<IndividualPhoneRecordWrapper>> tryFindEveryByNumbers(Phone p1,
        List<? extends BasePhoneNumber> bNums, boolean createIfAbsent)

        IndividualPhoneRecords.tryFindEveryByNumbers(p1, bNums, createIfAbsent
            .then { Map<PhoneNumber, List<IndividualPhoneRecord>> numToPRecs ->
                List<IndividualPhoneRecord> iprs = CollectionUtils.mergeUnique(*numToPRecs.values())
                List<PhoneRecord> sharedRecs = new DetachedCriteria(PhoneRecord)
                    .build(PhoneRecords.forActive())
                    .build(PhoneRecords.forShareSourceIds(iprs*.id))
                    .list()
                CollectionUtils.mergeUnique(sharedRecs*.toWrapper(), iprs*.toWrapper())
            }
    }

    // Helpers
    // -------

    static DetachedCriteria<PhoneRecord> buildBase(String query,
        Collection<PhoneRecordStatus> statuses) {

        new DetachedCriteria(PhoneRecord)
            .build { ne("class", GroupPhoneRecord) } // ensure only owned or shared individuals
            .build(forStatuses(phoneId, statuses))
            .build(forQuery(query))
            .build(PhoneRecords.forActive())
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forStatuses(Long phoneId,
        Collection<PhoneRecordStatus> statuses) {

        return { CriteriaUtils.inList(delegate, "status", statuses) }
    }

    // For hasMany left join, see: https://stackoverflow.com/a/45193881
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forQuery(String query) {
        if (!query) {
            return { }
        }
        return {
            // need to use createAlias because this property is not on the superclass
            createAlias("numbers", "indNumbers1", CriteriaSpecification.LEFT_JOIN)
            or {
                // TODO how does same property name for two subclasses work??
                String formattedQuery = StringUtils.toQuery(query)
                ilike("name", formattedQuery)
                shareSource(CriteriaSpecification.LEFT_JOIN) { ilike("name", formattedQuery) }
                // don't include the numbers constraint if not number or else will match all
                String cleanedAsNumber = StringUtils.cleanPhoneNumber(query)
                if (cleanedAsNumber) {
                    String numberQuery = StringUtils.toQuery(cleanedAsNumber)
                    ilike("indNumbers1.number", numberQuery)
                    shareSource(CriteriaSpecification.LEFT_JOIN) {
                        numbers(CriteriaSpecification.LEFT_JOIN) { ilike("number", numberQuery) }
                    }
                }
            }
        }
    }

    // For why sorting is separate, see `RecordItems.forSort`
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forSort() {
        return {
            order("status", "desc") // unread first then active
            order("record.lastRecordActivity", "desc") // more recent first
        }
    }
}
