package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.textup.*
import org.textup.rest.*
import org.textup.type.*

@GrailsCompileStatic
class MediaInfoJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyMediaInfo mInfo ->

        Map json = [id: mInfo.id, images: mInfo.getMediaElementsByType(MediaType.IMAGE_TYPES)]

        Result<?> res = Helpers.tryGetFromRequest(Constants.REQUEST_UPLOAD_ERRORS)
            .logFail("MediaInfoJsonMarshaller: no available request", LogLevel.DEBUG)
        if (res.success) { json.uploadErrors = res.payload }

        json
    }

    MediaInfoJsonMarshaller() {
        super(ReadOnlyMediaInfo, marshalClosure)
    }
}
