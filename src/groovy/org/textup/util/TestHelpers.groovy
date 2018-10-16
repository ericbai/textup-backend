package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import groovy.transform.TypeCheckingMode
import groovy.xml.MarkupBuilder
import java.nio.file.*
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.quartz.*
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.media.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
class TestHelpers {

    private static final Random RANDOM = new Random()
    private static final MockMessageSource MESSAGE_SOURCE = new MockMessageSource()
    private static final HashSet<String> _generatedPhoneNumbers = new HashSet<>()
    private static final ConfigObject CONFIG = new ConfigSlurper()
        .parse(new File("grails-app/conf/Config.groovy").toURL())

    // Display
    // -------

    static Map jsonToMap(JSON json) {
        Helpers.toJson(json.toString()) as Map
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static String buildXml(Closure data) {
        StringWriter writer = new StringWriter()
        MarkupBuilder xmlBuilder = new MarkupBuilder(writer)
        xmlBuilder(data)
        writer.toString().replaceAll(/<call>|<\/call>|\s+/, "").trim()
    }

    // Utilities
    // ---------

    static String randPhoneNumber() {
        String randNumber = generatePhoneNumber()
        while (_generatedPhoneNumbers.contains(randNumber)) {
            randNumber = generatePhoneNumber()
        }
        _generatedPhoneNumbers.add(randNumber)
        randNumber
    }
    private static String generatePhoneNumber() {
        int randString = TestHelpers.randIntegerUpTo(Math.pow(10, 10) as Integer)
        "${Constants.TEST_DEFAULT_AREA_CODE}${randString}".padRight(10, "0")[0..9]
    }

    static int randIntegerUpTo(Integer max) {
        RANDOM.nextInt(max)
    }

    static String randString() {
        UUID.randomUUID().toString()
    }

    static String encodeBase64String(byte[] rawData) {
        Base64.encodeBase64String(rawData)
    }

    static String getChecksum(String encodedData) {
        DigestUtils.md5Hex(encodedData)
    }

    static String buildVeryLongString() {
        StringBuilder sBuilder = new StringBuilder()
        Constants.MAX_TEXT_COLUMN_SIZE.times { it -> sBuilder << it }
        sBuilder.toString()
    }

    // Media
    // -----

    static byte[] getSampleDataForMimeType(MediaType type) {
        switch (type) {
            case MediaType.IMAGE_PNG:
                return TestHelpers.getPngSampleData()
            case MediaType.IMAGE_JPEG:
                return TestHelpers.getJpegSampleData512()
            case MediaType.IMAGE_GIF:
                return TestHelpers.getGifSampleData()
            case MediaType.AUDIO_MP3:
                return TestHelpers.getSampleData("audio.mp3")
            case MediaType.AUDIO_OGG_VORBIS:
                return TestHelpers.getSampleData("audio-vorbis.ogg")
            case MediaType.AUDIO_OGG_OPUS:
                return TestHelpers.getSampleData("audio-opus.ogg")
            case MediaType.AUDIO_WEBM_VORBIS:
                return TestHelpers.getSampleData("audio-vorbis.webm")
            case MediaType.AUDIO_WEBM_OPUS:
                return TestHelpers.getSampleData("audio-opus.webm")
            default:
                return [] as byte[]
        }
    }

    static Map buildAddMediaAction(MediaType type) {
        byte[] data = TestHelpers.getSampleDataForMimeType(type)
        String encodedData = TestHelpers.encodeBase64String(data)
        [
            action: Constants.MEDIA_ACTION_ADD,
            mimeType: type.mimeType,
            data: encodedData,
            checksum: TestHelpers.getChecksum(encodedData)
        ]
    }

    static byte[] getJpegSampleData512() {
        getSampleData("512x512.jpeg")
    }

    static byte[] getJpegSampleData256() {
        getSampleData("256x256.jpeg")
    }

    static byte[] getPngSampleData() {
        getSampleData("800x600.png")
    }

    static byte[] getGifSampleData() {
        getSampleData("400x400.gif")
    }

    protected static byte[] getSampleData(String fileName) {
        String root = Paths.get(".").toAbsolutePath().normalize().toString()
        new FileInputStream("${root}/test/assets/${fileName}").withStream { InputStream iStream ->
            IOUtils.toByteArray(iStream)
        }
    }

    // Files
    // -----

    static void clearTempDirectory() {
        TestHelpers.tempDirectory.toFile().listFiles().each { File file -> file.delete() }
    }

    static int getNumInTempDirectory() {
        TestHelpers.tempDirectory.toFile().listFiles().length
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Path getTempDirectory() {
        String tempDirectory = TestHelpers.config.textup.tempDirectory
        Paths.get(tempDirectory)
    }

    // Mocking dependencies
    // --------------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static <T> T getBean(GrailsApplication grailsApplication, Class<T> beanName) {
        grailsApplication.mainContext.getBean(beanName)
    }

    static ConfigObject getConfig() {
        CONFIG
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static AudioUtils getAudioUtils() {
        String executableDirectory = TestHelpers.config.textup.media.audio.executableDirectory
        String executableName = TestHelpers.config.textup.media.audio.executableName
        String tempDirectory = TestHelpers.config.textup.tempDirectory
        new AudioUtils(executableDirectory, executableName, tempDirectory)
    }

    static ResultFactory getResultFactory(GrailsApplication grailsApplication) {
        TestHelpers.getBean(grailsApplication, ResultFactory)
    }

    static LinkGenerator mockLinkGenerator() {
        [link: { Map m -> (m.params ?: [:]).toString() }] as LinkGenerator
    }

    static Scheduler mockScheduler() {
        [getTrigger: { TriggerKey key -> null }] as Scheduler
    }

    static MessageSource mockMessageSource() {
        MESSAGE_SOURCE
    }

    // Object generators
    // -----------------

    static Location buildLocation() {
        Location loc1 = new Location(address:"Testing Address", lat:0G, lon:0G)
        loc1.save(flush:true, failOnError:true)
    }

    static MediaElement buildMediaElement(BigDecimal sendSize = 88) {
        MediaElement e1 = new MediaElement()
        e1.sendVersion = TestHelpers.buildMediaElementVersion(sendSize)
        assert e1.validate()
        e1
    }

    static UploadItem buildUploadItem(MediaType type = MediaType.AUDIO_MP3) {
        UploadItem uItem = new UploadItem(type: type, data: getSampleDataForMimeType(type))
        assert uItem.validate()
        uItem
    }

    static MediaElementVersion buildMediaElementVersion(BigDecimal sendSize = 88) {
        MediaElementVersion mVers1 = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: UUID.randomUUID().toString(),
            sizeInBytes: sendSize.longValue(),
            widthInPixels: 888)
        assert mVers1.validate()
        mVers1
    }

    static RecordItemReceipt buildReceipt(ReceiptStatus status = ReceiptStatus.PENDING) {
        RecordItemReceipt rpt = new RecordItemReceipt(status: status,
            contactNumberAsString: TestHelpers.randPhoneNumber(),
            apiId: UUID.randomUUID().toString())
        rpt
    }

    static TempRecordReceipt buildTempReceipt(ReceiptStatus status = ReceiptStatus.PENDING) {
        TempRecordReceipt rpt = new TempRecordReceipt(status: status,
            contactNumberAsString: TestHelpers.randPhoneNumber(),
            apiId: UUID.randomUUID().toString(),
            numSegments: TestHelpers.randIntegerUpTo(10))
        assert rpt.validate()
        rpt
    }

    static OutgoingMessage buildOutgoingMessage(String message = "hi") {
        OutgoingMessage text = new OutgoingMessage(message: message,
            contacts: new ContactRecipients(),
            sharedContacts: new SharedContactRecipients(),
            tags: new ContactTagRecipients())
        assert text.validate()
        text
    }

    // Mocking
    // -------

    static MockedMethod mock(Object obj, String methodName, Closure action = null) {
        new MockedMethod(obj, methodName, action)
    }
}
