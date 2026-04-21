package com.kevin.financeguardian.data.sms

import java.security.MessageDigest

object BodyHasher {
    fun sha256Hex(input: String): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
