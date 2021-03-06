package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class MailService {

    Result<Void> notifyInvitation(Staff invitedBy, Staff invited, String pwd, String lockCode) {
        MailData.InvitedStaff md1 = new MailData.InvitedStaff(inviter: invitedBy?.name,
            invitee: invited?.name,
            username: invited?.username,
            password: pwd,
            lockCode: lockCode)
        DomainUtils.tryValidate(md1)
            .then { EmailEntity.tryCreate(invited.name, invited.email) }
            .then { EmailEntity toEntity -> sendMail(toEntity, md1) }
    }

    Result<Void> notifyApproval(Staff s1) {
        MailData.ApprovedStaff md1 = new MailData.ApprovedStaff(name: s1?.name,
            username: s1?.username,
            org: s1?.org?.name)
        DomainUtils.tryValidate(md1)
            .then { EmailEntity.tryCreate(s1.name, s1.email) }
            .then { EmailEntity toEntity -> sendMail(toEntity, md1) }
    }

    Result<Void> notifyAboutPendingStaff(Staff pendingStaff, Collection<Staff> admins) {
        MailData.PendingStaff md1 = new MailData.PendingStaff(staff: pendingStaff?.name,
            org: pendingStaff?.org?.name)
        DomainUtils.tryValidate(md1)
            .then {
                ResultGroup.collect(admins) { Staff s1 -> EmailEntity.tryCreate(s1.name, s1.email) }
                    .toResult(false)
            }
            .then { List<EmailEntity> toEntities ->
                ResultGroup.collect(toEntities) { EmailEntity toEntity -> sendMail(toEntity, md1) }
                    .toEmptyResult(false)
            }
    }

    Result<Void> notifyAboutPendingOrg(Organization newOrg) {
        MailData.PendingOrg md1 = new MailData.PendingOrg(org: newOrg?.name)
        DomainUtils.tryValidate(md1)
            .then { sendMail(MailUtils.selfEntity(), md1) }
    }

    Result<Void> notifyRejection(Staff s1) {
        MailData.RejectedStaff md1 = new MailData.RejectedStaff(name: s1?.name,
            username: s1?.username)
        DomainUtils.tryValidate(md1)
            .then { EmailEntity.tryCreate(s1.name, s1.email) }
            .then { EmailEntity toEntity -> sendMail(toEntity, md1) }
    }

    Result<Void> notifyPasswordReset(Staff s1, Token tok1) {
        MailData.PasswordReset md1 = new MailData.PasswordReset(name: s1?.name,
            username: s1?.username,
            link: LinkUtils.passwordReset(tok1?.token))
        DomainUtils.tryValidate(md1)
            .then { EmailEntity.tryCreate(s1.name, s1.email) }
            .then { EmailEntity toEntity -> sendMail(toEntity, md1) }
    }

    Result<Void> notifyMessages(ReadOnlyStaff rs1, NotificationInfo notifInfo,
        NotificationFrequency freq1 = null, Token tok1 = null) {

        MailData.Notification md1 = new MailData.Notification(staffName: rs1?.name,
            phoneName: notifInfo?.phoneName,
            phoneNumber: notifInfo?.phoneNumber?.prettyPhoneNumber,
            timePeriodDescription: NotificationFrequency.descriptionWithFallback(freq1),
            incomingDescription: NotificationUtils.buildIncomingMessage(notifInfo),
            outgoingDescription: NotificationUtils.buildOutgoingMessage(notifInfo))
        if (notifInfo) {
            md1.numIncoming = notifInfo.numIncomingText + notifInfo.numIncomingCall
            md1.numOutgoing = notifInfo.numOutgoingText + notifInfo.numOutgoingCall
        }
        if (tok1) {
            md1.link = LinkUtils.notification(tok1.token)
        }
        DomainUtils.tryValidate(md1)
            .then { EmailEntity.tryCreate(rs1.name, rs1.email) }
            .then { EmailEntity toEntity -> sendMail(toEntity, md1) }
    }

    // Helpers
    // -------

    protected Result<Void> sendMail(EmailEntity toEntity, Object info) {
        String templateId = MailUtils.getTemplateId(info.class)
        MailUtils.send(MailUtils.defaultFromEntity(), toEntity, templateId, DomainUtils.instanceProps(info))
    }
}
