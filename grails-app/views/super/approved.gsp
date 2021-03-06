<!DOCTYPE html>
<html>
    <head>
        <title>TextUp Super Approved</title>
        <meta name="layout" content="main">
        <asset:stylesheet src="super.css"/>
    </head>
    <body>
        <div class="super-container">
            <g:if test="${flash.messages}">
                <div class="message">
                    <ul>
                        <g:each in="${flash.messages}">
                            <li>${it}</li>
                        </g:each>
                    </ul>
                </div>
            </g:if>
            <g:if test="${flash.errorObj}">
                <div class="message">
                    <ul>
                        <g:eachError bean="${flash.errorObj}">
                            <li>${it.defaultMessage}</li>
                        </g:eachError>
                    </ul>
                </div>
            </g:if>
            <p class="super-container-title">Approved Organizations</p>
            <g:if test="${orgs}">
                <g:each var="o" in="${orgs}">
                    <div class="unverified-org">
                        <div class="org-details">
                            <span class="name">${o.name}</span>
                            <span class="address">${o.location.address}</span>
                        </div>
                        <g:each var="a" in="${grailsApplication.mainContext.getBean('superService').getAdminsForOrgId(o.id)}">
                            <div class="org-admin">
                                <span class="name">${a.name}</span>
                                <span class="email">${a.email}</span>
                            </div>
                        </g:each>
                        <div class="org-controls">
                            <g:link action="rejectOrg" params="[id: "${o.id}"]" class="btn btn-danger">
                               Reject
                            </g:link>
                        </div>
                    </div>
                </g:each>
            </g:if>
            <g:else>
                <p class="super-container-none">No approved organizations.</p>
            </g:else>
        </div>
    </body>
</html>
