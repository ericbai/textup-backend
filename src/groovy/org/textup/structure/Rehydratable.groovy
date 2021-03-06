package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface Rehydratable<T> {
    Result<T> tryRehydrate()
}
