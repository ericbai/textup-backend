package org.textup

import com.amazonaws.services.cloudfront.CloudFrontUrlSigner
import com.amazonaws.services.cloudfront.CloudFrontUrlSigner.Protocol
import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class LinkUtils {

    // for voicemail greetings to enable caching
    // With a single behavior in one CloudFront distribution, cannot support both signed and
    // unsigned URLs. For this edge case of voicemail greetings, just use the s3 url
    static URL unsignedLink(String ident) {
        if (!ident) {
            return
        }
        try {
            String bucketName = Holders.flatConfig["textup.media.bucketName"]
            new URL(Constants.PROTOCOL_HTTPS, "s3.amazonaws.com", "/${bucketName}/${ident}")
        }
        catch (Throwable e) {
            log.error("unsignedLink: ${e.class}, ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // It is acceptable to call this method multiple times even for the same object
    // because presigned url is generated locally. Therefore, we don't have to worry
    // about the additional complexity of caching these results because we don't
    // have to worry about the additional performance penalty of network requests.
    static URL signedLink(String ident) {
        if (!ident) {
            return
        }
        try {
            String root = Holders.flatConfig["textup.media.cdn.root"],
                keyId = Holders.flatConfig["textup.media.cdn.keyId"],
                privateKeyPath = Holders.flatConfig["textup.media.cdn.privateKeyPath"]
            File keyFile = new File(privateKeyPath)
            Date expiresAt = DateTime.now().plusHours(1).toDate()
            getSignedLink(Protocol.https, root, keyFile, ident, keyId, expiresAt)
        }
        catch (Throwable e) {
            log.error("signedLink: ${e.class}, ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // Helpers
    // -------

    protected static URL getSignedLink(Protocol protocol, String root, File keyFile,
        String identifier, String keyId, Date expiresAt) {
        new URL(CloudFrontUrlSigner
            .getSignedURLWithCannedPolicy(protocol, root, keyFile, identifier, keyId, expiresAt))
    }
}