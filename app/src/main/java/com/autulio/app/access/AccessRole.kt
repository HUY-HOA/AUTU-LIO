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

fun AccessRole.defaultPermissions(): Set<AccessPermission> = when (this) {
    AccessRole.OWNER -> AccessPermission.entries.toSet()
    AccessRole.DEVELOPER -> setOf(
        AccessPermission.VIEW_ADMIN_WORKSPACE,
        AccessPermission.EDIT_SCRIPTS,
        AccessPermission.TEST_SCRIPTS,
        AccessPermission.SUBMIT_RELEASE
    )
    AccessRole.DESIGNER -> setOf(
        AccessPermission.VIEW_ADMIN_WORKSPACE,
        AccessPermission.EDIT_UI_DESIGN,
        AccessPermission.PREVIEW_UI,
        AccessPermission.SUBMIT_RELEASE
    )
    AccessRole.REVIEWER -> setOf(
        AccessPermission.VIEW_ADMIN_WORKSPACE,
        AccessPermission.TEST_SCRIPTS,
        AccessPermission.REVIEW_RELEASE
    )
    AccessRole.CUSTOMER -> setOf(
        AccessPermission.RUN_PUBLISHED_SCRIPTS,
        AccessPermission.VIEW_OWN_ACCOUNT
    )
}

fun AccessRole.configurablePermissions(): Set<AccessPermission> = when (this) {
    AccessRole.DEVELOPER -> setOf(
        AccessPermission.EDIT_SCRIPTS,
        AccessPermission.TEST_SCRIPTS,
        AccessPermission.SUBMIT_RELEASE
    )
    AccessRole.DESIGNER -> setOf(
        AccessPermission.EDIT_UI_DESIGN,
        AccessPermission.PREVIEW_UI,
        AccessPermission.SUBMIT_RELEASE
    )
    AccessRole.REVIEWER -> setOf(
        AccessPermission.TEST_SCRIPTS,
        AccessPermission.REVIEW_RELEASE
    )
    AccessRole.OWNER,
    AccessRole.CUSTOMER -> emptySet()
}

fun AccessRole.allows(permission: AccessPermission): Boolean =
    permission in defaultPermissions()

fun AccessPermission.labelText(): String = when (this) {
    AccessPermission.VIEW_ADMIN_WORKSPACE -> trPermission("Truy cập không gian quản trị", "Open admin workspace")
    AccessPermission.MANAGE_COLLABORATORS -> trPermission("Quản lý cộng tác viên", "Manage collaborators")
    AccessPermission.MANAGE_CUSTOMERS_AND_LICENSES -> trPermission("Quản lý khách hàng và giấy phép", "Manage customers and licenses")
    AccessPermission.EDIT_SCRIPTS -> trPermission("Viết và sửa mã kịch bản", "Write and edit script source")
    AccessPermission.TEST_SCRIPTS -> trPermission("Kiểm thử kịch bản", "Test scripts")
    AccessPermission.EDIT_UI_DESIGN -> trPermission("Thiết kế giao diện", "Edit interface design")
    AccessPermission.PREVIEW_UI -> trPermission("Xem trước giao diện", "Preview interface")
    AccessPermission.SUBMIT_RELEASE -> trPermission("Gửi bản để duyệt", "Submit changes for review")
    AccessPermission.REVIEW_RELEASE -> trPermission("Duyệt hoặc từ chối bản gửi", "Review or reject submissions")
    AccessPermission.PUBLISH_RELEASE -> trPermission("Phát hành chính thức", "Publish official releases")
    AccessPermission.RUN_PUBLISHED_SCRIPTS -> trPermission("Chạy kịch bản đã phát hành", "Run published scripts")
    AccessPermission.VIEW_OWN_ACCOUNT -> trPermission("Xem tài khoản của mình", "View own account")
}

private fun trPermission(vi: String, en: String): String =
    if (java.util.Locale.getDefault().language == "vi") vi else en
