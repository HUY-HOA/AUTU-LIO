package com.autulio.app.access

enum class AccessRole {
    OWNER,
    CUSTOMER
}

enum class AccessPermission {
    VIEW_ADMIN_WORKSPACE,
    MANAGE_CUSTOMERS_AND_LICENSES,
    EDIT_SCRIPTS,
    TEST_SCRIPTS,
    PUBLISH_RELEASE,
    RUN_PUBLISHED_SCRIPTS,
    VIEW_OWN_ACCOUNT
}

fun AccessRole.defaultPermissions(): Set<AccessPermission> = when (this) {
    AccessRole.OWNER -> AccessPermission.entries.toSet()
    AccessRole.CUSTOMER -> setOf(
        AccessPermission.RUN_PUBLISHED_SCRIPTS,
        AccessPermission.VIEW_OWN_ACCOUNT
    )
}

fun AccessRole.allows(permission: AccessPermission): Boolean =
    permission in defaultPermissions()
