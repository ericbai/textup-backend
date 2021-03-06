package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.joda.time.DateTime
import org.joda.time.format.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.annotation.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(["ROLE_USER", "ROLE_ADMIN"])
@Transactional
class RecordController extends BaseController {

    PdfService pdfService
    RecordService recordService

    @Override
    def index() {
        TypeMap qParams = TypeMap.create(params)
        ControllerUtils.tryGetPhoneId(qParams.long("teamId"))
            .then { Long pId ->
                RequestUtils.trySet(RequestUtils.PHONE_ID, pId)
                RecordUtils.buildRecordItemRequest(pId, qParams)
            }
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { RecordItemRequest iReq ->
                if (qParams.format == ControllerUtils.FORMAT_PDF) {
                    RequestUtils.trySet(RequestUtils.PAGINATION_OPTIONS, qParams)
                    RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
                    String ts = JodaUtils.FILE_TIMESTAMP_FORMAT.print(DateTime.now())
                    respondWithPdf("textup-export-${ts}.pdf", pdfService.buildRecordItems(iReq))
                }
                else {
                    respondWithCriteria(iReq.criteria,
                        qParams,
                        RecordItems.forSort(true),
                        MarshallerUtils.KEY_RECORD_ITEM)
                }
            }
    }

    @Override
    def show() {
        Long id = params.long("id")
        doShow({ RecordItems.isAllowed(id) }, { RecordItems.mustFindForId(id) })
    }

    @OptimisticLockingRetry
    @Override
    def save() {
        TypeMap qParams = TypeMap.create(params)
        doSave(MarshallerUtils.KEY_RECORD_ITEM, request, recordService) { TypeMap body ->
            body.timezone = qParams.string("timezone")
            ControllerUtils.tryGetPhoneId(qParams.long("teamId"))
                .then { Long pId ->
                    RequestUtils.trySet(RequestUtils.PHONE_ID, pId)
                    IOCUtils.resultFactory.success(pId)
                }
        }
    }

    @Override
    def update() {
        TypeMap qParams = TypeMap.create(params)
        doUpdate(MarshallerUtils.KEY_RECORD_ITEM, request, recordService) { TypeMap body ->
            body.timezone = qParams.string("timezone")
            RecordItems.isAllowed(params.long("id"))
        }
    }

    @Override
    def delete() {
        doDelete(recordService) { RecordItems.isAllowed(params.long("id")) }
    }

    // Helpers
    // -------

    protected void respondWithPdf(String fileName, Result<byte[]> pdfRes) {
        // step 1: status
        renderStatus(pdfRes.status)
        // step 2: payload
        if (pdfRes.success) {
            withPDFFormat {
                InputStream iStream = new ByteArrayInputStream(pdfRes.payload)
                iStream.withCloseable {
                    render(file: iStream,
                        fileName: fileName,
                        contentType: ControllerUtils.CONTENT_TYPE_PDF)
                }
            }
        }
        else {
            withJsonFormat {
                respond(ControllerUtils.buildErrors(pdfRes))
            }
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected void withPDFFormat(Closure doPDF) {
        withFormat { pdf(doPDF) }
    }
}
