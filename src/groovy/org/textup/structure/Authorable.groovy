package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface Authorable {
    String getAuthorName()
    Long getAuthorId()
    AuthorType getAuthorType()
}
