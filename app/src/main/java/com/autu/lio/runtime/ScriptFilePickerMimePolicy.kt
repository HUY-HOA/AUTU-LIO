package com.autu.lio.runtime

/**
 * Accepts a bounded MIME type or a type with a wildcard subtype (for example the image type with any subtype).
 * A wildcard major type is rejected so a script cannot silently broaden its filter.
 */
object ScriptFilePickerMimePolicy {
    private const val MAX_MIME_LENGTH = 127
    private const val TOKEN_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#&^_.+-"

    fun isValid(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_MIME_LENGTH) return false
        val separator = value.indexOf('/')
        if (separator <= 0 || separator != value.lastIndexOf('/')) return false
        val type = value.substring(0, separator)
        val subtype = value.substring(separator + 1)
        if (type == "*" || type.isEmpty() || subtype.isEmpty()) return false
        if (!type.all { it in TOKEN_CHARACTERS }) return false
        return subtype == "*" || subtype.all { it in TOKEN_CHARACTERS }
    }
}
