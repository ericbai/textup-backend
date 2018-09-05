package org.textup

import grails.compiler.GrailsCompileStatic
import org.textup.type.MediaType
import org.textup.type.MediaVersion

// The interfaces that define the contract for handling media

@GrailsCompileStatic
interface ReadOnlyWithMedia {
    ReadOnlyMediaInfo getReadOnlyMedia()
}

@GrailsCompileStatic
interface WithMedia extends ReadOnlyWithMedia {
    void setMedia(MediaInfo mInfo)
    MediaInfo getMedia()
}

@GrailsCompileStatic
interface ReadOnlyMediaInfo {
    Long getId()
    List<? extends ReadOnlyMediaElement> getMediaElementsByType()
    List<? extends ReadOnlyMediaElement> getMediaElementsByType(Collection<MediaType> typesToRetrieve)
}

@GrailsCompileStatic
interface ReadOnlyMediaElement {
    String getUid()
    MediaType type
    Map<MediaVersion, ? extends ReadOnlyMediaElementVersion> getVersionsForDisplay()
}

@GrailsCompileStatic
interface ReadOnlyMediaElementVersion {
    URL getLink()
    Integer getInherentWidth()
    Integer getHeightInPixels()
}
