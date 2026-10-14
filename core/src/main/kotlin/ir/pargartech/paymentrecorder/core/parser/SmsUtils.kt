package ir.pargartech.paymentrecorder.core.parser

import java.security.MessageDigest

object Dedupe {
    /**
     * Stable id for one SMS. The same message seen by the live receiver and by an inbox
     * scan hashes the same as long as both report the same timestamp to the minute; the
     * app additionally checks for identical bodies a few minutes apart.
     */
    fun hash(sender: String, body: String, timestampMillis: Long): String {
        val input = "${SenderDirectory.normalize(sender)}|${body.trim()}|${timestampMillis / 60_000}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object SmsMasker {
    /**
     * Masks a sample before the user shares it for parser tuning: every digit becomes 7
     * (keeping Persian/Arabic/ASCII script and all separators, so the format survives).
     * Names can't be detected reliably, so the UI asks the user to check the text.
     */
    fun mask(body: String): String = buildString(body.length) {
        for (ch in body) {
            append(
                when (ch) {
                    in '0'..'9' -> '7'
                    in '۰'..'۹' -> '۷'
                    in '٠'..'٩' -> '٧'
                    else -> ch
                },
            )
        }
    }
}
