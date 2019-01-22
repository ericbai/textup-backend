package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.apache.commons.lang3.ClassUtils
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.textup.*

@GrailsTypeChecked
@Log4j
class TypeConversionUtils {

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static <T extends Enum<T>> T convertEnum(Class<T> enumClass, def string) {
        String enumString = string?.toString()?.toUpperCase()
        enumClass?.values().find { it.toString() == enumString } ?: null
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static <T extends Enum<T>> List<T> toEnumList(Class<T> enumClass, def enumsOrStrings,
        List<T> fallbackVal = []) {
        if (enumsOrStrings instanceof Collection) {
            enumsOrStrings
                ?.collect { enumOrString ->
                    enumClass?.isInstance(enumOrString)
                        ? enumOrString
                        : convertEnum(enumClass, enumOrString)
                }
                ?: fallbackVal
        }
        else { fallbackVal }
    }

    // For some reason, cannot combine these two method signatures into one using a default
    // value for the fallbackValue. Doing so causes the static compilation to fail to convert
    // the Object val to the generic type T, instead complaining that the return value is of
    // type T and does not match te declared type
    static <T> T to(Class<T> clazz, Object val) {
        TypeConversionUtils.to(clazz, val, null)
    }
    static <T> T to(Class<T> clazz, Object val, T fallbackVal) {
        if (val == null) {
            return fallbackVal
        }
        Class<T> wrappedClazz = ClassUtils.primitiveToWrapper(clazz)
        try {
            String str = "${val}".toLowerCase()
            switch (wrappedClazz) {
                // note for String conversion we use default toString method on `val` not `str`
                case String: return val?.toString() ?: fallbackVal
                case Boolean: return (str == "true" || str == "false") ? str.toBoolean() : fallbackVal
                case Number: return str.isBigDecimal() ? str.toBigDecimal().asType(wrappedClazz) : fallbackVal
                default: return wrappedClazz.isAssignableFrom(val.getClass()) ? val.asType(wrappedClazz) : fallbackVal
            }
        }
        catch (ClassCastException e) {
            log.debug("TypeConversionUtils.to: wrappedClazz: $wrappedClazz, val: $val: ${e.message}")
            fallbackVal
        }
    }

    static <T> List<T> allTo(Class<T> clazz, Collection<? extends Object> val) {
        allTo(clazz, val, null)
    }
    static <T> List<T> allTo(Class<T> clazz, Collection<? extends Object> val, T replaceFailWith) {
        List<T> results = []
        if (!val) {
            return results
        }
        for (obj in val) {
            results << to(clazz, obj, replaceFailWith)
        }
        CollectionUtils.ensureNoNull(results)
    }
}
