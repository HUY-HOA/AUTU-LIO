package com.autulio.app.access

enum class AccessRole {
    OWNER,
    DEVELOPER,
    DESIGNER,
    REVIEWER,
    CUSTOMER
}

enum class AccessPermission {
    VIEW_ADMIN_WORKSPACE,
    MANAGE_COLLABORATORS,
    MANAGE_CUSTOMERS_AND_LICENSES,
    EDIT_SCRIPTS,
    TEST_SCRIPTS,
    EDIT_UI_DESIGN,
    PREVIEW_UI,
    SUBMIT_RELEASE,
    REVIEW_RELEASE,
    PUBLISH_RELEASE,
    RUN_PUBLISHED_SCRIPTS,
    VIEW_OWN_ACCOUNT
}

fun AccessRole.allows(permission: AccessPermission): Boolean = when (this) {
    AccessRole.OWNER -> true
    AccessRole.DEVELOPER -> permission in setOf(
        AccessPermission.VIEW_ADMIN_WORKSPACE,
        AccessPermission.EDIT_SCRIPTS,
        AccessPermission.TEST_SCRIPTS,
        AccessPermission.SUBMIT_RELEASE
    )
    AccessRole.DESIGNER -> permission in setOf(
        AccessPermission.VIEW_ADMIN_WORKSPACE,
        AccessPermission.EDIT_UI_DESIGN,
        AccessPermission.PREVIEW_UI
    )
    AccessRole.REVIEWER -> permission in setOf(
        AccessPermission.VIEW_ADMIN_WORKSPACE,
        AccessPermission.TEST_SCRIPTS,
        AccessPermission.REVIEW_RELEASE
    )
    AccessRole.CUSTOMER -> permission in setOf(
        AccessPermission.RUN_PUBLISHED_SCRIPTS,
        AccessPermission.VIEW_OWN_ACCOUNT
    )
}
