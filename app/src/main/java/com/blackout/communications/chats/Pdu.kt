package com.blackout.communications.chats

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Reads and writes MMS messages in their binary (WAP/MMS PDU) format, on the phone.
 * Covers what a 1-to-1 picture message needs: the download notice, the downloaded
 * message, and a send request with text and pictures.
 */
object Pdu {
    const val TYPE_SEND_REQ = 0x80
    const val TYPE_NOTIFICATION_IND = 0x82
    const val TYPE_RETRIEVE_CONF = 0x84

    private const val H_CONTENT_LOCATION = 0x83
    private const val H_CONTENT_TYPE = 0x84
    private const val H_DATE = 0x85
    private const val H_FROM = 0x89
    private const val H_MESSAGE_TYPE = 0x8C
    private const val H_MMS_VERSION = 0x8D
    private const val H_SUBJECT = 0x96
    private const val H_TO = 0x97
    private const val H_TRANSACTION_ID = 0x98

    class Part(val contentType: String, val data: ByteArray, val charset: Int = 106, val name: String? = null) {
        val isText get() = contentType.startsWith("text/plain")
        val isImage get() = contentType.startsWith("image/")
        val isVideo get() = contentType.startsWith("video/")
        fun text(): String = String(data, charsetOf(charset))
    }

    class Message(
        val type: Int,
        val transactionId: String?,
        val contentLocation: String?,
        val from: String?,
        val to: List<String>,
        val dateSec: Long?,
        val subject: String?,
        val parts: List<Part>,
    )

    // ---------------------------------------------------------------- Reading

    fun parse(data: ByteArray): Message? = runCatching { Reader(data).message() }.getOrNull()

    /** Last-resort way to find the download address in a notice that couldn't be read normally. */
    fun findLocation(data: ByteArray): String? =
        Regex("https?://[\\x21-\\x7E]+").find(String(data, Charsets.ISO_8859_1))?.value

    private class Reader(val d: ByteArray) {
        var p = 0

        fun u(i: Int) = d[i].toInt() and 0xFF

        fun message(): Message {
            var type = 0
            var tid: String? = null
            var location: String? = null
            var from: String? = null
            val to = mutableListOf<String>()
            var date: Long? = null
            var subject: String? = null
            var parts = emptyList<Part>()
            // A message with an odd header is kept with whatever was read before it, not thrown away.
            try {
                while (p < d.size) {
                    val field = u(p)
                    if (field and 0x80 == 0) {
                        // An unknown text-named header: skip its name and value.
                        textString(); skipValue(); continue
                    }
                    p++
                    when (field) {
                        H_MESSAGE_TYPE -> type = u(p++)
                        H_TRANSACTION_ID -> tid = textString()
                        H_CONTENT_LOCATION -> location = textString()
                        H_FROM -> from = fromValue()
                        H_TO -> to += cleanAddress(encodedString())
                        H_DATE -> date = longInteger()
                        H_SUBJECT -> subject = encodedString()
                        H_CONTENT_TYPE -> {
                            val ct = contentType()
                            if (type == TYPE_RETRIEVE_CONF && ct.first.startsWith("application/vnd.wap.multipart")) {
                                parts = multipart()
                            } else if (type == TYPE_RETRIEVE_CONF) {
                                parts = listOf(Part(ct.first, d.copyOfRange(p, d.size), ct.second))
                            }
                            p = d.size
                        }
                        else -> skipValue()
                    }
                }
            } catch (e: RuntimeException) {
                if (type != TYPE_RETRIEVE_CONF && type != TYPE_NOTIFICATION_IND) throw e
            }
            return Message(type, tid, location, from?.let { cleanAddress(it) }, to, date, subject, parts)
        }

        fun uintvar(): Long {
            var v = 0L
            while (true) {
                val b = u(p++)
                v = (v shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) return v
            }
        }

        /** Length of a value-length field; leaves p at the value's first byte. */
        fun valueLength(): Int {
            val b = u(p)
            return when {
                b <= 30 -> { p++; b }
                b == 31 -> { p++; uintvar().toInt() }
                else -> error("not a value-length")
            }
        }

        fun textString(): String {
            if (u(p) == 0x7F) p++ // quote
            val start = p
            while (p < d.size && d[p].toInt() != 0) p++
            val s = String(d, start, p - start, Charsets.UTF_8)
            p++ // the 0x00
            return s
        }

        fun skipValue() {
            val b = u(p)
            when {
                b <= 30 -> p += 1 + b
                b == 31 -> { p++; val len = uintvar().toInt(); p += len }
                b < 128 -> textString()
                else -> p++
            }
        }

        fun longInteger(): Long {
            val len = u(p++)
            var v = 0L
            repeat(len) { v = (v shl 8) or u(p++).toLong() }
            return v
        }

        fun integer(): Long = if (u(p) >= 0x80) (u(p++) and 0x7F).toLong() else longInteger()

        fun encodedString(): String {
            val b = u(p)
            if (b <= 31) {
                val len = valueLength()
                val end = p + len
                val charset = integer().toInt()
                val s = if (u(p) == 0x7F) { p++; String(d, p, end - p - 1, charsetOf(charset)) } else String(d, p, (end - p - 1).coerceAtLeast(0), charsetOf(charset))
                p = end
                return s
            }
            return textString()
        }

        fun fromValue(): String? {
            val len = valueLength()
            val end = p + len
            val token = u(p++)
            val address = if (token == 0x80) encodedString() else null // 0x81 = "insert my address"
            p = end
            return address
        }

        /** Returns (media type, charset). */
        fun contentType(): Pair<String, Int> {
            val b = u(p)
            return when {
                b >= 0x80 -> { p++; (WELL_KNOWN.getOrNull(b and 0x7F) ?: "application/octet-stream") to 106 }
                b in 32..127 -> textString() to 106
                else -> {
                    val len = valueLength()
                    val end = p + len
                    val media = if (u(p) >= 0x80) WELL_KNOWN.getOrNull(u(p++) and 0x7F) ?: "application/octet-stream" else textString()
                    var charset = 106
                    while (p < end) {
                        val param = u(p)
                        if (param == 0x81) { p++; charset = integer().toInt() }
                        else if (param >= 0x80) { p++; skipValue() }
                        else { textString(); skipValue() }
                    }
                    p = end
                    media to charset
                }
            }
        }

        fun multipart(): List<Part> {
            val parts = ArrayList<Part>()
            try {
                val count = uintvar().toInt()
                repeat(count) {
                    val headersLen = uintvar().toInt()
                    val dataLen = uintvar().toInt()
                    val headersEnd = p + headersLen
                    val (ct, charset) = contentType()
                    var name: String? = null
                    while (p < headersEnd) {
                        val h = u(p)
                        if (h == 0x8E) { p++; name = textString() } // Content-Location
                        else if (h >= 0x80) { p++; skipValue() }
                        else { textString(); skipValue() }
                    }
                    p = headersEnd
                    parts += Part(ct, d.copyOfRange(p, p + dataLen), charset, name)
                    p += dataLen
                }
            } catch (e: RuntimeException) {
                // A damaged part ends the list; the parts before it are still shown.
            }
            return parts
        }
    }

    // ---------------------------------------------------------------- Writing

    /** A send request to one person, with text and/or pictures. */
    fun sendRequest(to: String, transactionId: String, parts: List<Part>): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(H_MESSAGE_TYPE); o.write(TYPE_SEND_REQ)
        o.write(H_TRANSACTION_ID); writeText(o, transactionId)
        o.write(H_MMS_VERSION); o.write(0x92) // 1.2
        o.write(H_FROM); o.write(1); o.write(0x81) // the network fills in our number
        o.write(H_TO); writeText(o, "${to.filter { it.isDigit() || it == '+' }}/TYPE=PLMN")
        o.write(H_CONTENT_TYPE); o.write(0xA3) // application/vnd.wap.multipart.mixed
        writeUintvar(o, parts.size.toLong())
        parts.forEachIndexed { i, part ->
            val headers = ByteArrayOutputStream()
            if (part.isText) {
                headers.write(3); headers.write(0x83); headers.write(0x81); headers.write(0xEA) // text/plain; charset=utf-8
            } else {
                writeText(headers, part.contentType)
            }
            headers.write(0x8E); writeText(headers, part.name ?: (if (part.isText) "text$i.txt" else "image$i.jpg"))
            val h = headers.toByteArray()
            writeUintvar(o, h.size.toLong())
            writeUintvar(o, part.data.size.toLong())
            o.write(h)
            o.write(part.data)
        }
        return o.toByteArray()
    }

    private fun writeText(o: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) o.write(0x7F)
        o.write(bytes)
        o.write(0)
    }

    private fun writeUintvar(o: ByteArrayOutputStream, value: Long) {
        val groups = ArrayList<Int>()
        var v = value
        do { groups.add((v and 0x7F).toInt()); v = v shr 7 } while (v > 0)
        for (i in groups.indices.reversed()) o.write(if (i > 0) groups[i] or 0x80 else groups[i])
    }

    fun cleanAddress(a: String): String = a.substringBefore("/TYPE=").trim()

    fun charsetOf(mib: Int): Charset = when (mib) {
        3 -> Charsets.US_ASCII
        4 -> Charsets.ISO_8859_1
        1000, 1015 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    }

    private val WELL_KNOWN = listOf(
        "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml", "text/x-vCalendar", "text/x-vCard",
        "text/vnd.wap.wml", "text/vnd.wap.wmlscript", "text/vnd.wap.wta-event", "multipart/*", "multipart/mixed",
        "multipart/form-data", "multipart/byterantes", "multipart/alternative", "application/*", "application/java-vm",
        "application/x-www-form-urlencoded", "application/x-hdmlc", "application/vnd.wap.wmlc", "application/vnd.wap.wmlscriptc",
        "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof", "application/vnd.wap.wtls-ca-certificate",
        "application/vnd.wap.wtls-user-certificate", "application/x-x509-ca-cert", "application/x-x509-user-cert",
        "image/*", "image/gif", "image/jpeg", "image/tiff", "image/png", "image/vnd.wap.wbmp",
        "application/vnd.wap.multipart.*", "application/vnd.wap.multipart.mixed", "application/vnd.wap.multipart.form-data",
        "application/vnd.wap.multipart.byteranges", "application/vnd.wap.multipart.alternative", "application/xml", "text/xml",
        "application/vnd.wap.wbxml", "application/x-x968-cross-cert", "application/x-x968-ca-cert", "application/x-x968-user-cert",
        "text/vnd.wap.si", "application/vnd.wap.sic", "text/vnd.wap.sl", "application/vnd.wap.slc", "text/vnd.wap.co",
        "application/vnd.wap.coc", "application/vnd.wap.multipart.related",
    )
}
