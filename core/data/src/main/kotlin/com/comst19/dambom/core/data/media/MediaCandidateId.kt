package com.comst19.dambom.core.data.media

import java.security.MessageDigest

internal fun String.mediaCandidateId(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }
