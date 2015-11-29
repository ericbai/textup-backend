<!DOCTYPE html>
<html>
    <head>
        <title>TextUp Super Settings</title>
        <meta name="layout" content="main">
        <asset:stylesheet src="super.css"/>
    </head>
    <body>
        <div class="super-container">
            <g:if test="${flash.message}">
                <div class="message">${flash.message}</div>
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
            <p class="super-container-title">Super Settings</p>
            <g:form name="superSettings" controller="super">
                <div class="form-group">
                    <label for="name">Name</label>
                    <g:field class="form-control" type="text" name="name" required="" value="${staff.name}"/>
                </div>
                <div class="form-group">
                    <label for="username">Username</label>
                    <g:field class="form-control" type="text" name="username" required="" value="${staff.username}"/>
                </div>
                <div class="form-group">
                    <label for="password">Password</label>
                    <g:field class="form-control" type="password" name="password"/>
                </div>
                <div class="form-group">
                    <label for="confirmPassword">Confirm Password</label>
                    <g:field class="form-control" type="password" name="confirmPassword"/>
                </div>
                <div class="form-group">
                    <label for="email">Email</label>
                    <g:field class="form-control" type="email" name="email"required="" value="${staff.email}"/>
                </div>
                <g:actionSubmit value="Update" class="btn btn-default" action="updateSettings"/>
            </g:form>
        </div>
    </body>
</html>
