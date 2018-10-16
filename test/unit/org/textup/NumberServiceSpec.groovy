package org.textup

import grails.test.mixin.TestFor
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

@TestFor(NumberService)
class NumberServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        Helpers.metaClass."static".getMessageSource = { -> TestHelpers.mockMessageSource() }
        service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
    }

    void "test start verifying ownership"() {
        given:
        PhoneNumber invalidNum = new PhoneNumber(number: "abc")
        PhoneNumber validNum = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        assert invalidNum.validate() == false
        assert validNum.validate()
        service.tokenService = Mock(TokenService)
        service.textService = Mock(TextService)

        when: "input is not valid"
        Result<Void> res = service.startVerifyOwnership(invalidNum)

        then:
        0 * service.tokenService._
        0 * service.textService._
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        Helpers.metaClass."static".tryGetNotificationNumber = { ->
            new Result(payload: new PhoneNumber())
        }
        res = service.startVerifyOwnership(validNum)

        then:
        1 * service.tokenService.generateVerifyNumber(*_) >> new Result(payload: [token: "hi"] as Token)
        1 * service.textService.send(*_) >> new Result()
        res.status == ResultStatus.OK
    }

    void "test finish verifying ownership"() {
        given:
        service.tokenService = Mock(TokenService)

        when:
        Result<Void> res = service.finishVerifyOwnership(null, null)

        then:
        1 * service.tokenService.verifyNumber(*_) >> new Result()
        res.status == ResultStatus.OK
    }

    void "test cleaning number search query"() {
        expect:
        service.cleanQuery(null) == ""
        service.cleanQuery("&&&!@#abcABC123") == "abcABC123"
    }
}
