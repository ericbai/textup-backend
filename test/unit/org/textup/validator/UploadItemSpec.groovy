package org.textup.validator

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import javax.imageio.*
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.springframework.validation.Errors
import org.textup.*
import org.textup.type.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class UploadItemSpec extends Specification {

    void "test constraints"() {
        when: "empty upload item"
        UploadItem uItem = new UploadItem()

        then:
        uItem.validate() == false
        uItem.errors.errorCount == 4 // 3 actual props + `type` custom getter

        when: "invalid mimeType"
        uItem.mimeType = "invalid MIME type"

        then:
        uItem.validate() == false
        uItem.errors.getFieldErrorCount("mimeType") == 1
        uItem.errors.errorCount == 4

        when: "invalidly encoded data"
        uItem.mediaVersion = MediaVersion.SEND
        uItem.mimeType = Constants.MIME_TYPE_JPEG
        uItem.data = "not actually an image".bytes

        then: "valid, assume data is valid, even if it isn't a valid image"
        uItem.validate() == true
        uItem.widthInPixels == 0 // 0, not actually an image so can't process into image to find width
    }

    void "test getting writers for various mime types WITHOUT exception catching"() {
        expect:
        MediaType.IMAGE.mimeTypes.every { String mType -> UploadItem.getWriter(mType) != null }
    }

    void "test converting byte data to a BufferedImage WITHOUT exception catching"() {
        given:
        byte[] jpegTest = getJpegSampleData512()
        byte[] pngTest = getPngSampleData()
        assert jpegTest != null
        assert pngTest != null

        expect: "valid image input data to return a BufferedImage"
        UploadItem.tryGetImageFromData(jpegTest) instanceof BufferedImage
        UploadItem.tryGetImageFromData(pngTest) instanceof BufferedImage

        and: "invalid image input data to return null"
        UploadItem.tryGetImageFromData("not valid image data".bytes) == null
    }

    void "test converting Image to BufferedImage WITHOUT exception catching"() {
        given: "an image"
        BufferedImage bImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)

        expect:
        UploadItem.imageToBufferedImage(bImg) instanceof BufferedImage
    }

    void "test ability to compress various mime types"() {
        expect:
        UploadItem.canCompress(Constants.MIME_TYPE_JPEG) == true
        UploadItem.canCompress(Constants.MIME_TYPE_PNG) == false
        UploadItem.canCompress(Constants.MIME_TYPE_GIF) == true
        UploadItem.canCompress("something else") == false
    }

    // jpeg size fluctuates on decoding + encoding due to stripping of metadata and rounding errors
    // during the forward and reverse processes. See https://stackoverflow.com/a/29274870
    // The only want to ensure the exact same byte data is to copy byte by byte
    void "test repeated encoding + decoding jpeg WITHOUT exception catching"() {
        given: "encode our initial jpeg image from byte data"
        ImageWriter writer = UploadItem.getWriter(Constants.MIME_TYPE_JPEG)
        byte[] initialData = getJpegSampleData512()
        BufferedImage jpgImg = UploadItem.tryGetImageFromData(initialData)
        assert jpgImg != null
        ImageWriteParam param1 = writer.defaultWriteParam

        when: "convert with default params (compresssion cannot be turned off for jpeg)"
        byte[] convert1 = UploadItem.getDataFromImage(jpgImg, writer, param1)

        then: "compression cannot be turned off + jpeg encoding/decoding has size fluctuations"
        initialData.length > convert1.length

        when: "convert a second time"
        BufferedImage jpgImg2 = UploadItem.tryGetImageFromData(convert1)
        byte[] convert2 = UploadItem.getDataFromImage(jpgImg2, writer, param1)

        then: "similar size to first conversion within a wide band"
        initialData.length > convert2.length
        Math.abs(convert2.length - convert1.length) < 10000

        when: "convert a third time"
        BufferedImage jpgImg3 = UploadItem.tryGetImageFromData(convert2)
        byte[] convert3 = UploadItem.getDataFromImage(jpgImg3, writer, param1)

        then: "similar size to first two conversions within a wide band"
        initialData.length > convert3.length
        Math.abs(convert3.length - convert1.length) < 10000
        Math.abs(convert3.length - convert2.length) < 10000
    }

    void "test decoding jpeg with compression"() {
        given: "encode our initial jpeg image from byte data"
        ImageWriter writer = UploadItem.getWriter(Constants.MIME_TYPE_JPEG)
        byte[] initialData = getJpegSampleData512()
        BufferedImage jpgImg = UploadItem.tryGetImageFromData(initialData)
        assert jpgImg != null

        when: "convert with no compression (cannot turn compression off for jpeg)"
        ImageWriteParam param1 = writer.defaultWriteParam
        param1.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
        param1.setCompressionQuality(1.0f)
        byte[] convert1 = UploadItem.getDataFromImage(jpgImg, writer, param1)

        then:
        convert1 != null
        initialData.length > convert1.length

        when: "convert with compression params"
        ImageWriteParam param2 = UploadItem.tryGetCompressionParamsForWriter(writer, 0.5f)
        byte[] convert2 = UploadItem.getDataFromImage(jpgImg, writer, param2)

        then: "byte result has a smaller size"
        convert2 != null
        convert1.length > convert2.length
    }

    void "test trying to compress png results in exception"() {
        given: "encode our initial jpeg image from byte data"
        ImageWriter writer = UploadItem.getWriter("image/png")
        byte[] initialData = getPngSampleData()
        BufferedImage pngImg = UploadItem.tryGetImageFromData(initialData)
        assert pngImg != null

        when: "try to convert png witout compression"
        ImageWriteParam param1 = writer.defaultWriteParam
        byte[] convert1 = UploadItem.getDataFromImage(pngImg, writer, param1)

        then: "ok, but decoded byte data will not be same as the original"
        convert1 != null
        initialData.length != convert1.length

        when: "when we try to convert png with compression"
        ImageWriteParam param2 = UploadItem.tryGetCompressionParamsForWriter(writer, 0.5f)
        byte[] convert2 = UploadItem.getDataFromImage(pngImg, writer, param2)

        then: "compression not supported at all for png"
        thrown UnsupportedOperationException
    }

    void "test trying to compress gif requires setting compression type"() {
        given: "encode our initial jpeg image from byte data"
        ImageWriter writer = UploadItem.getWriter("image/gif")
        byte[] initialData = getGifSampleData()
        BufferedImage gifImg = UploadItem.tryGetImageFromData(initialData)
        assert gifImg != null

        when: "try to convert png witout compression"
        ImageWriteParam param1 = writer.defaultWriteParam
        byte[] convert1 = UploadItem.getDataFromImage(gifImg, writer, param1)

        then: "ok, but decoded byte data will not be same as the original"
        convert1 != null
        initialData.length != convert1.length

        when: "when we try to convert png with compression"
        ImageWriteParam param2 = UploadItem.tryGetCompressionParamsForWriter(writer, 0.1f)
        byte[] convert2 = UploadItem.getDataFromImage(gifImg, writer, param2)

        then: "can be compressed, even if the compressed size is the same as the non-compressed"
        convert2 != null
    }

    void "test custom getters"() {
        when: "obj with valid mime type"
        byte[] inputData1 = getJpegSampleData512()
        UploadItem uItem = new UploadItem(mediaVersion: MediaVersion.SEND,
            mimeType: Constants.MIME_TYPE_JPEG,
            data: inputData1)
        assert uItem.validate()

        then: "can get MediaType enum"
        uItem.type == MediaType.IMAGE
        uItem.widthInPixels == 512
        uItem.sizeInBytes == inputData1.size()

        when: "setting data"
        Long width1 = uItem.widthInPixels
        byte[] inputData2 = getJpegSampleData256()
        assert inputData1.size() != inputData2.size()
        uItem.data = inputData2

        then: "image private property is also implicitly set + can get file size and image width"
        uItem.sizeInBytes == inputData2.size()
        uItem.widthInPixels == 256
    }

    void "test resizing width"() {
        given: "obj with data"
        Helpers.metaClass.'static'.getResultFactory = { -> mockResultFactory() }
        byte[] inputData1 = getPngSampleData()
        UploadItem uItem = new UploadItem(mediaVersion: MediaVersion.SEND,
            mimeType: Constants.MIME_TYPE_PNG,
            data: inputData1)
        assert uItem.validate()

        when: "resize to a zero width"
        Result<UploadItem> res = uItem.tryResizeToWidth(0)

        then: "short circuit -- see mock"
        res.payload == "uploadItem.tryResizeToWidth.invalidWidth"

        when: "resize to a negative width"
        res = uItem.tryResizeToWidth(-1)

        then: "short circuit -- see mock"
        res.payload == "uploadItem.tryResizeToWidth.invalidWidth"

        when: "resize to a positive width"
        float aspectRatio = 0.8
        int targetWidth = Math.floor(uItem.widthInPixels * aspectRatio)
        res = uItem.tryResizeToWidth(targetWidth)

        then: "successfully do so while preserving the aspect ratio"
        res.payload instanceof UploadItem
        targetWidth == res.payload.widthInPixels
    }

    void "test compression short circuiting"() {
        given: "obj representing a PNG image (not compressible)"
        Helpers.metaClass.'static'.getResultFactory = { -> mockResultFactory() }
        byte[] inputData1 = getPngSampleData()
        UploadItem uItem = new UploadItem(mediaVersion: MediaVersion.SEND,
            mimeType: Constants.MIME_TYPE_PNG,
            data: inputData1)
        assert uItem.validate()

        when: "compress to a zero size"
        Result<UploadItem> res = uItem.tryCompress(0)

        then: "short circuit without infinite loop"
        res.payload == "uploadItem.tryCompress.invalidSize"

        when: "compress to a negative size"
        res = uItem.tryCompress(-1)

        then: "short circuit without infinite loop"
        res.payload == "uploadItem.tryCompress.invalidSize"
    }

    void "test compression"() {
        given: "obj with compressible data"
        Helpers.metaClass.'static'.getResultFactory = { -> mockResultFactory() }
        byte[] inputData1 = getJpegSampleData512()
        UploadItem uItem = new UploadItem(mediaVersion: MediaVersion.SEND,
            mimeType: Constants.MIME_TYPE_JPEG,
            data: inputData1)
        assert uItem.validate()

        when: "compress to impossibly small size"
        int targetSize = 1
        Result<UploadItem> res = uItem.tryCompress(targetSize)

        then: "short circuit before hitting file size b/c of min quality standards"
        res.success == true
        res.payload instanceof UploadItem
        res.payload.sizeInBytes < inputData1.length
        res.payload.sizeInBytes > targetSize // impossibly small threshold

        when: "compress to more reasonable size"
        uItem.data = inputData1
        targetSize = inputData1.length * 0.8
        res = uItem.tryCompress(targetSize)

        then: "successfully compress to be smaller than the max size threshold"
        res.success == true
        res.payload instanceof UploadItem
        res.payload.sizeInBytes < inputData1.length
        res.payload.sizeInBytes <= targetSize
    }

    // Helpers
    // -------

    protected ResultFactory mockResultFactory() {
        [
            failWithValidationErrors: { Errors errors ->
                new Result(status: ResultStatus.UNPROCESSABLE_ENTITY, payload: errors)
            },
            failWithCodeAndStatus: { String code, ResultStatus stat1 ->
                new Result(status: stat1, payload: code)
            },
            success: { Object obj ->
                new Result(payload: obj)
            }
        ] as ResultFactory
    }

    protected byte[] getJpegSampleData512() {
        getSampleData("512x512.jpeg")
    }

    protected byte[] getJpegSampleData256() {
        getSampleData("256x256.jpeg")
    }

    protected byte[] getPngSampleData() {
        getSampleData("800x600.png")
    }

    protected byte[] getGifSampleData() {
        getSampleData("400x400.gif")
    }

    protected byte[] getSampleData(String fileName) {
        String root = Paths.get(".").toAbsolutePath().normalize().toString()
        new FileInputStream("${root}/test/assets/${fileName}").withStream { InputStream iStream ->
            IOUtils.toByteArray(iStream)
        }
    }
}
