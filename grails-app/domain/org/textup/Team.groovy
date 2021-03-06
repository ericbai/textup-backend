package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Team implements WithId, CanSave<Team> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    boolean isDeleted = false
    DateTime whenCreated = JodaUtils.utcNow()
    Location location
    Organization org
    String hexColor = Constants.DEFAULT_BRAND_COLOR
    String name

    static hasMany = [members: Staff]
    static mapping = {
        cache usage: "read-write", include: "non-lazy"
        whenCreated type: PersistentDateTime
        // [NOTE] one-to-many relationships should not have `fetch: "join"` because of GORM using
        // a left outer join to fetch the data runs into issues when a max is provided
        // see: https://stackoverflow.com/a/25426734
        members cascade: "save-update"
        location fetch: "join", cascade: "save-update"
    }
    static constraints = {
    	name validator: { String val, Team obj ->
            //within an org, team name must be unique
            if (val && Utils.<Boolean>doWithoutFlush {
                    Teams.buildActiveForOrgIdAndName(obj.org?.id, val)
                        .build(CriteriaUtils.forNotIdIfPresent(obj.id))
                        .count() > 0
                }) {
                ["team.name.duplicate", obj.org?.name]
            }
        }
        hexColor validator: { String val ->
            if (!ValidationUtils.isValidHexCode(val)) { ["team.hexColor.invalidHex"] }
        }
        location cascadeValidation: true
    }

    static Result<Team> tryCreate(Organization org1, String name, Location loc1) {
        DomainUtils.trySave(new Team(org: org1, name: name, location: loc1), ResultStatus.CREATED)
    }

    // Properties
    // ----------

    Collection<Staff> getActiveMembers() {
        members?.findAll { Staff s1 -> s1.status.isActive() } ?: new ArrayList<Staff>()
    }

    // Can't move to static class because Grails manages this relationship so no direct queries
    Collection<Staff> getMembersByStatus(Collection<StaffStatus> statuses) {
        if (statuses) {
            HashSet<StaffStatus> statusesToFind = new HashSet<>(statuses)
            members?.findAll { Staff s1 -> statusesToFind.contains(s1.status) }
        }
        else { members }
    }
}
