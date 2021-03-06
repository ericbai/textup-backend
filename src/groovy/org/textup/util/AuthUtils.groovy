package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.plugin.springsecurity.userdetails.NoStackUsernameNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.authentication.encoding.PasswordEncoder // deprecated
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class AuthUtils {

    // `loadCurrentUser` returns a `Staff` proxy class for only `id` property access
    // see: http://grails-plugins.github.io/grails-spring-security-core/2.0.x/guide/helperClasses.html#springSecurityService
    static Result<Long> tryGetAuthId() {
        if (IOCUtils.security.isLoggedIn()) {
            Staff authUserProxy = IOCUtils.security.loadCurrentUser() as Staff
            IOCUtils.resultFactory.success(authUserProxy.id)
        }
        else { forbidden() }
    }

    static Result<Staff> tryGetAnyAuthUser() {
        AuthUtils.tryGetAuthId()
            .then { Long authId ->
                Staff s1 = Staff.get(authId)
                s1 ? IOCUtils.resultFactory.success(s1) : forbidden()
            }
    }

    static Result<Staff> tryGetActiveAuthUser() {
        AuthUtils.tryGetAnyAuthUser()
            .then { Staff s1 -> isActive(s1) ? IOCUtils.resultFactory.success(s1) : forbidden() }
    }

    static Result<Void> isAllowed(boolean outcome) {
        outcome ? Result.void() : forbidden()
    }

    // see: http://blog.cwill-dev.com/2011/05/11/grails-springsecurityservice-authenticate-via-code-manually/
    static boolean isValidCredentials(String username, String password) {
        try {
            UserDetailsService userDetailsService = IOCUtils.security.userDetailsService as UserDetailsService
            UserDetails details = userDetailsService.loadUserByUsername(username)
            IOCUtils.authProvider
                .authenticate(new UsernamePasswordAuthenticationToken(details, password))
                .authenticated
        }
        catch (NoStackUsernameNotFoundException | BadCredentialsException e) {
            return false
        }
    }

    static String encodeSecureString(String val) {
        PasswordEncoder encoder = IOCUtils.security.passwordEncoder as PasswordEncoder
        encoder.encodePassword(val, null)
    }

    static boolean isSecureStringValid(String reference, String val) {
        PasswordEncoder encoder = IOCUtils.security.passwordEncoder as PasswordEncoder
        encoder.isPasswordValid(reference, val, null)
    }

    // Helpers
    // -------

    protected static boolean isActive(Staff s1) {
        StaffStatus.ACTIVE_STATUSES.contains(s1?.status) &&
        OrgStatus.ACTIVE_STATUSES.contains(s1?.org?.status)
    }

    protected static Result<?> forbidden() {
        IOCUtils.resultFactory.failWithCodeAndStatus("authUtils.forbidden",
            ResultStatus.FORBIDDEN)
    }
}
