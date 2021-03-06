package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode(includeFields = true)
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
@Validateable
class Recipients implements CanValidate {

    final Collection<? extends PhoneRecord> all
    final Integer maxNum
    final Phone phone
    final VoiceLanguage language
    final boolean countAsRecords // so that GroupPhoneRecords also count their members

    static constraints = { // all nullable: false by default
        maxNum min: 1
        all minSize: 1, validator: { Collection<? extends PhoneRecord> val, Recipients obj ->
            if (val) {
                if (val.findAll { !it.toPermissions().canModify() }) {
                    return ["someNoPermissions", val*.id]
                }
                int numRecips = obj.buildCount()
                if (numRecips > obj.maxNum) {
                    return ["tooManyRecipients", numRecips, obj.maxNum]
                }
            }
        }
    }

    static Result<Recipients> tryCreate(Phone p1, Collection<Long> prIds,
        Collection<PhoneNumber> pNums, int maxNum, boolean countAsRecords = false) {
        // create new contacts as needed
        IndividualPhoneRecords.tryFindOrCreateNumToObjByPhoneAndNumbers(p1, pNums, true, true)
            .then { Map<PhoneNumber, Collection<IndividualPhoneRecord>> ipRecs ->
                // then fetch the shared contacts + tags
                Collection<? extends PhoneRecord> pRecs = PhoneRecords
                    .buildNotExpiredForPhoneIds([p1?.id])
                    .build(PhoneRecords.forIds(prIds))
                    .list()
                pRecs.addAll(CollectionUtils.mergeUnique(ipRecs.values()))
                Recipients.tryCreateForPhoneAndObjs(p1, pRecs, p1?.language, maxNum, countAsRecords)
            }
    }

    static Result<Recipients> tryCreate(Collection<? extends PhoneRecord> pRecs,
        VoiceLanguage language, int maxNum, boolean countAsRecords = false) {

        Recipients.tryCreateForPhoneAndObjs(pRecs?.getAt(0)?.phone, pRecs, language, maxNum, countAsRecords)
    }

    static Result<Recipients> tryCreateForPhoneAndObjs(Phone p1, Collection<? extends PhoneRecord> thisPhoneRecs,
        VoiceLanguage language, int maxNum, boolean countAsRecords = false) {

        Collection<? extends PhoneRecord> prs = thisPhoneRecs ?: new ArrayList<? extends PhoneRecord>()
        Recipients r1 = new Recipients(Collections.unmodifiableCollection(prs),
            maxNum,
            p1,
            language,
            countAsRecords)
        DomainUtils.tryValidate(r1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    Result<PhoneRecordWrapper> tryGetOne() {
        PhoneRecord pr1 = all?.getAt(0)
        pr1 ?
            IOCUtils.resultFactory.success(pr1.toWrapper()) :
            IOCUtils.resultFactory.failWithCodeAndStatus("recipients.hasNone",
                ResultStatus.UNPROCESSABLE_ENTITY)
    }

    Result<IndividualPhoneRecordWrapper> tryGetOneIndividual() {
        IndividualPhoneRecordWrapper w1
        if (all) {
            w1 = all*.toWrapper()
                .find { it instanceof IndividualPhoneRecordWrapper } as IndividualPhoneRecordWrapper
        }
        w1 ?
            IOCUtils.resultFactory.success(w1) :
            IOCUtils.resultFactory.failWithCodeAndStatus("recipients.hasNoIndividuals",
                ResultStatus.UNPROCESSABLE_ENTITY)
    }

    String buildFromName() { phone?.owner?.buildName() }

    int buildCount() {
        countAsRecords ? CollectionUtils.mergeUnique(all*.buildRecords()).size() : all.size()
    }

    // loops through unique records for individuals, groups, and group members
    void eachRecord(Closure action) {
        CollectionUtils.mergeUnique(all*.buildRecords()).each(action)
    }

    // loops through owned and shared `IndividualPhoneRecord`s, calling each passing the
    // `IndividualPhoneRecordWrapper` and all relevant individual and group records
    void eachIndividualWithRecords(Closure action) {
        Collection<GroupPhoneRecord> groups = []
        HashSet<? extends PhoneRecord> individualPhoneRecs = new HashSet<>()
        all?.each { PhoneRecord pr1 ->
            if (pr1 instanceof GroupPhoneRecord) {
                GroupPhoneRecord gpr1 = pr1 as GroupPhoneRecord
                groups << gpr1
                individualPhoneRecs.addAll(gpr1.members.allActive)
            }
            else { individualPhoneRecs << pr1 }
        }
        Map<Long, Collection<GroupPhoneRecord>> idToGroups = PhoneRecordUtils.buildMemberIdToGroups(groups)
        individualPhoneRecs.each { PhoneRecord pr1 ->
            PhoneRecordWrapper w1 = pr1.toWrapper()
            if (w1 instanceof IndividualPhoneRecordWrapper) {
                action.call(w1, CollectionUtils.mergeUnique([[pr1.record], idToGroups[pr1.id]*.record]))
            }
        }
    }
}
