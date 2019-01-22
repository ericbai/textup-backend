package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class DomainUtils {

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Object tryGetId(Object obj) {
        obj?.metaClass?.hasProperty(obj, "id") ? obj.id : null
    }

    static boolean isNew(Object obj) {
        getId(obj) == null
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static boolean hasDirtyNonObjectFields(Object obj, Collection<String> propsToIgnore) {
        if (obj.metaClass.hasProperty("dirtyPropertyNames")) {
            List<String> dirtyProps = obj.dirtyPropertyNames
            !dirtyProps.isEmpty() &&
                dirtyProps.findAll { !propsToIgnore.contains(it) }.size() > 0
        }
        else { false }
    }

     static <T extends Saveable> Result<T> trySave(T obj, ResultStatus status = ResultStatus.OK) {
        if (obj) {
            if (obj.save()) {
                IOCUtils.resultFactory.success(obj, status)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(obj.errors) }
        }
        else { invalidInput() }
    }

    static <T extends Saveable> Result<Void> trySaveAll(Collection<T> objList) {
        if (objList == null) {
            ResultGroup<T> resGroup = new ResultGroup<>()
            objList.each { T obj -> resGroup << DomainUtils.trySave(obj) }
            if (resGroup.anyFailures) {
                IOCUtils.resultFactory.failWithGroup(resGroup)
            }
            else { Result.void() }
        }
        else { invalidInput() }
    }

    static <T extends Validateable> Result<T> tryValidate(T obj,
        ResultStatus status = ResultStatus.OK) {

        if (obj) {
            if (obj.validate()) {
                IOCUtils.resultFactory.success(obj, status)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(obj.errors) }
        }
        else { invalidInput() }
    }

    static <T extends Validateable> Result<Void> tryValidateAll(Collection<T> objList) {
        if (objList == null) {
            ResultGroup<T> resGroup = new ResultGroup<>()
            objList.each { T obj -> resGroup << DomainUtils.tryValidate(obj) }
            if (resGroup.anyFailures) {
                IOCUtils.resultFactory.failWithGroup(resGroup)
            }
            else { Result.void() }
        }
        else { invalidInput() }
    }

    // Helpers
    // -------

    protected static Result<?> invalidInput() {
        IOCUtils.resultFactory.failWithCodeAndStatus("", // TODO
            ResultStatus.INTERNAL_SERVER_ERROR)
    }
}
