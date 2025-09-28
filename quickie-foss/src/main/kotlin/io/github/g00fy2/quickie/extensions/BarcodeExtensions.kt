package io.github.g00fy2.quickie.extensions

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import com.google.zxing.Result
import com.google.zxing.client.result.AddressBookParsedResult
import com.google.zxing.client.result.CalendarParsedResult
import com.google.zxing.client.result.EmailAddressParsedResult
import com.google.zxing.client.result.GeoParsedResult
import com.google.zxing.client.result.ResultParser
import com.google.zxing.client.result.SMSParsedResult
import com.google.zxing.client.result.TelParsedResult
import com.google.zxing.client.result.URIParsedResult
import com.google.zxing.client.result.WifiParsedResult
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_BYTES
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_PARCELABLE
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_TYPE
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_VALUE
import io.github.g00fy2.quickie.content.AddressParcelable
import io.github.g00fy2.quickie.content.CalendarDateTimeParcelable
import io.github.g00fy2.quickie.content.CalendarEventParcelable
import io.github.g00fy2.quickie.content.ContactInfoParcelable
import io.github.g00fy2.quickie.content.EmailParcelable
import io.github.g00fy2.quickie.content.GeoPointParcelable
import io.github.g00fy2.quickie.content.PersonNameParcelable
import io.github.g00fy2.quickie.content.PhoneParcelable
import io.github.g00fy2.quickie.content.SmsParcelable
import io.github.g00fy2.quickie.content.UrlBookmarkParcelable
import io.github.g00fy2.quickie.content.WifiParcelable
import java.util.Calendar
import java.util.Locale

data object DataType {
  const val TYPE_UNKNOWN = 0
  const val TYPE_WORK = 1
  const val TYPE_HOME = 2
  const val TYPE_FAX = 3
  const val TYPE_MOBILE = 4

  object Wifi {
    const val TYPE_OPEN = 1
    const val TYPE_WPA = 2
    const val TYPE_WEP = 3
  }
}

/**
 * Попытаться максимально заполнить Parcelable-структуры, парся raw текст (vCard / MECARD / mailto / matmsg и т.д.)
 */
internal fun Result.toParcelableContentType(): Parcelable? {
  val parsed = ResultParser.parseResult(this)
  val raw = this.text
  val vcardMeta by lazy { parseVCardOrMecard(raw) }

  return when (parsed) {
    is AddressBookParsedResult -> {
      ContactInfoParcelable(
        addressParcelables = parsed.addresses?.mapIndexed { idx, addr ->
          val normalized = normalizeForMatch(addr)
          val type =
            vcardMeta.addresses.find { normalizeForMatch(it.value) == normalized }?.type ?: DataType.TYPE_UNKNOWN
          AddressParcelable(addressLines = addr.split("\n"), type = type)
        }.orEmpty(),
        emailParcelables = parsed.emails?.map { email ->
          val type =
            vcardMeta.emails.find { it.value.equals(email, ignoreCase = true) }?.type ?: DataType.TYPE_UNKNOWN
          EmailParcelable(address = email, body = "", subject = "", type = type)
        }.orEmpty(),
        nameParcelable = vcardMeta.name?.toPersonNameParcelable()?.copy(
          pronunciation = parsed.pronunciation
        ) ?: PersonNameParcelable(
          first = "",
          formattedName = parsed.names?.firstOrNull().orEmpty(),
          last = "",
          middle = "",
          prefix = "",
          pronunciation = parsed.pronunciation,
          suffix = ""
        ),
        organization = parsed.org.orEmpty(),
        phoneParcelables = parsed.phoneNumbers?.map { phone ->
          val normalizedPhone = normalizePhone(phone)
          val type = vcardMeta.phones.find {
            normalizePhone(it.value) == normalizedPhone
          }?.type ?: DataType.TYPE_UNKNOWN
          PhoneParcelable(
            number = phone,
            type = type
          )
        }.orEmpty(),
        title = parsed.title.orEmpty(),
        urls = parsed.urLs?.toList().orEmpty()
      )
    }

    is EmailAddressParsedResult -> {
      // ZXing EmailParsed can have tos/body/subject (parsed.tos / parsed.subject / parsed.body)
      val mailtoMeta = parseMailTo(raw)
      val address = parsed.tos?.firstOrNull().orEmpty().ifEmpty { mailtoMeta?.to ?: "" }
      val subject = parsed.subject.orEmpty().ifEmpty { mailtoMeta?.subject.orEmpty() }
      val body = parsed.body.orEmpty().ifEmpty { mailtoMeta?.body.orEmpty() }
      val type = vcardMeta.emails.find {
        it.value.equals(address, ignoreCase = true)
      }?.type ?: DataType.TYPE_UNKNOWN

      EmailParcelable(
        address = address,
        body = body,
        subject = subject,
        type = type
      )
    }

    is TelParsedResult -> {
      val num = parsed.number.orEmpty()
      val type = vcardMeta.phones.find {
        normalizePhone(it.value) == normalizePhone(num)
      }?.type ?: DataType.TYPE_UNKNOWN
      PhoneParcelable(number = num, type = type)
    }

    is SMSParsedResult -> SmsParcelable(
      message = parsed.body.orEmpty(),
      phoneNumber = parsed.numbers?.firstOrNull().orEmpty()
    )

    is URIParsedResult -> UrlBookmarkParcelable(
      title = parsed.title.orEmpty(),
      url = parsed.uri.orEmpty()
    )

    is WifiParsedResult -> {
      val enc = when (parsed.networkEncryption?.uppercase()) {
        "WPA", "WPA2" -> DataType.Wifi.TYPE_WPA
        "WEP" -> DataType.Wifi.TYPE_WEP
        else -> DataType.Wifi.TYPE_OPEN
      }
      WifiParcelable(
        encryptionType = enc,
        password = parsed.password.orEmpty(),
        ssid = parsed.ssid.orEmpty()
      )
    }

    is GeoParsedResult -> GeoPointParcelable(
      lat = parsed.latitude,
      lng = parsed.longitude
    )

    is CalendarParsedResult -> CalendarEventParcelable(
      description = parsed.description.orEmpty(),
      end = parsed.endTimestamp.toParcelableCalendarEvent(),
      location = parsed.location.orEmpty(),
      organizer = parsed.organizer.orEmpty(),
      start = parsed.startTimestamp.toParcelableCalendarEvent(),
      status = extractCalendarStatus(raw),
      summary = parsed.summary.orEmpty()
    )

    else -> null
  }
}

internal fun Result.toIntent(): Intent {
  val parsed = ResultParser.parseResult(this)
  return Intent().apply {
    putExtra(EXTRA_RESULT_BYTES, rawBytes)
    putExtra(EXTRA_RESULT_VALUE, text)
    putExtra(EXTRA_RESULT_TYPE, parsed.type.ordinal)
    putExtra(EXTRA_RESULT_PARCELABLE, toParcelableContentType())
  }
}

private data class TypedValue(val value: String, val type: Int)
private data class VCardMeta(
  val phones: List<TypedValue> = emptyList(),
  val emails: List<TypedValue> = emptyList(),
  val addresses: List<TypedValue> = emptyList(),
  val name: VCardName? = null
)

private data class VCardName(
  val first: String?,
  val last: String?,
  val middle: String?,
  val prefix: String?,
  val suffix: String?,
  val formatted: String?
) {
  fun toPersonNameParcelable() = PersonNameParcelable(
    first = first.orEmpty(),
    formattedName = formatted.orEmpty(),
    last = last.orEmpty(),
    middle = middle.orEmpty(),
    prefix = prefix.orEmpty(),
    pronunciation = "",
    suffix = suffix.orEmpty()
  )
}

private fun parseVCardOrMecard(raw: String?): VCardMeta {
  if (raw == null) return VCardMeta()

  // try vCard block
  val vcardBlock = Regex("BEGIN:VCARD[\\s\\S]*?END:VCARD", RegexOption.IGNORE_CASE).find(raw)?.value
  if (vcardBlock != null) return parseVCardBlock(vcardBlock)

  // try MECARD form (MECARD:N:Lastname,Firstname;TEL:...;EMAIL:...;;)
  if (raw.startsWith("MECARD:", ignoreCase = true)) return parseMecard(raw)

  // fallback: nothing found
  return VCardMeta()
}

private fun parseVCardBlock(block: String): VCardMeta {
  // unfold folded lines (very simple approach)
  val unfolded = block.replace("\r\n ", "").replace("\n ", "\n")
  val phones = mutableListOf<TypedValue>()
  val emails = mutableListOf<TypedValue>()
  val addrs = mutableListOf<TypedValue>()
  var name: VCardName? = null

  val lines = unfolded.split(Regex("\\r?\\n"))
  val telRegex = Regex("^TEL(?:;([^:]+))?:(.+)$", RegexOption.IGNORE_CASE)
  val emailRegex = Regex("^EMAIL(?:;([^:]+))?:(.+)$", RegexOption.IGNORE_CASE)
  val adrRegex = Regex("^ADR(?:;([^:]+))?:(.+)$", RegexOption.IGNORE_CASE)
  val nRegex = Regex("^N:(.*)$", RegexOption.IGNORE_CASE)
  val fnRegex = Regex("^FN:(.*)$", RegexOption.IGNORE_CASE)

  lines.forEach { rawLine ->
    val line = rawLine.trim()
    telRegex.find(line)?.let {
      val attrs = it.groupValues[1]
      val value = it.groupValues[2].trim()
      phones.add(TypedValue(value, typeFromAttr(attrs)))
      return@forEach
    }
    emailRegex.find(line)?.let {
      val attrs = it.groupValues[1]
      val value = it.groupValues[2].trim()
      emails.add(TypedValue(value, typeFromAttr(attrs)))
      return@forEach
    }
    adrRegex.find(line)?.let { found ->
      val attrs = found.groupValues[1]
      val value = found.groupValues[2].trim()
      // ADR parts separated by ';' — join non-empty parts to single line for matching
      val parts = value.split(';').filter { p -> p.isNotBlank() }
      val addressJoined = parts.joinToString(", ") { it.trim() }
      addrs.add(TypedValue(addressJoined, typeFromAttr(attrs)))
      return@forEach
    }
    nRegex.find(line)?.let {
      val value = it.groupValues[1].trim()
      // N:Last;First;Middle;Prefix;Suffix
      val parts = value.split(';')
      val last = parts.getOrNull(0)?.ifBlank { null }
      val first = parts.getOrNull(1)?.ifBlank { null }
      val middle = parts.getOrNull(2)?.ifBlank { null }
      val prefix = parts.getOrNull(3)?.ifBlank { null }
      val suffix = parts.getOrNull(4)?.ifBlank { null }
      name = VCardName(first = first, last = last, middle = middle, prefix = prefix, suffix = suffix, formatted = null)
      return@forEach
    }
    fnRegex.find(line)?.let {
      val formatted = it.groupValues[1].trim()
      name =
        if (name == null) VCardName(
          first = null,
          last = null,
          middle = null,
          prefix = null,
          suffix = null,
          formatted = formatted
        )
        else name?.copy(formatted = formatted)
      return@forEach
    }
  }

  return VCardMeta(phones = phones, emails = emails, addresses = addrs, name = name)
}

private fun parseMecard(raw: String): VCardMeta {
  // MECARD:K:V;K:V;;  (разделитель ;)
  val body = raw.removePrefix("MECARD:").trimEnd(';')
  val parts = body.split(';').mapNotNull { it.ifBlank { null } }
  val phones = mutableListOf<TypedValue>()
  val emails = mutableListOf<TypedValue>()
  val addrs = mutableListOf<TypedValue>()
  var name: VCardName? = null

  parts.forEach { part ->
    val idx = part.indexOf(':')
    if (idx < 0) return@forEach
    val key = part.substring(0, idx).uppercase(Locale.ROOT)
    val value = part.substring(idx + 1)
    when (key) {
      "TEL" -> phones.add(TypedValue(value, DataType.TYPE_UNKNOWN))
      "EMAIL" -> emails.add(TypedValue(value, DataType.TYPE_UNKNOWN))
      "ADR" -> addrs.add(TypedValue(value, DataType.TYPE_UNKNOWN))
      "N" -> {
        // MECARD N:Lastname,Firstname
        val nmParts = value.split(',')
        val last = nmParts.getOrNull(0)?.ifBlank { null }
        val first = nmParts.getOrNull(1)?.ifBlank { null }
        name = VCardName(first = first, last = last, middle = null, prefix = null, suffix = null, formatted = null)
      }
      "NICKNAME" -> if (name == null) name =
        VCardName(first = value, last = null, middle = null, prefix = null, suffix = null, formatted = null)
    }
  }

  return VCardMeta(phones = phones, emails = emails, addresses = addrs, name = name)
}

/** простая эвристика извлечения типа из атрибутной строки vCard (TYPE=WORK, HOME, CELL, FAX и пр.) */
private fun typeFromAttr(attr: String?): Int {
  if (attr.isNullOrBlank()) return DataType.TYPE_UNKNOWN
  // удалим "TYPE=" если присутствует и разберём все токены
  val cleaned = attr.replace("TYPE=", "", ignoreCase = true)
  val tokens = cleaned.split(Regex("[,;:]")).map { it.trim().uppercase(Locale.ROOT) }

  if (tokens.any { it == "WORK" }) return DataType.TYPE_WORK
  if (tokens.any { it == "HOME" }) return DataType.TYPE_HOME
  if (tokens.any { it == "FAX" || it == "FAXWORK" || it == "FAXHOME" }) return DataType.TYPE_FAX
  if (tokens.any { it == "CELL" || it == "MOBILE" }) return DataType.TYPE_MOBILE

  // попытка распознать по простым ключевым словам
  if (tokens.any { it.contains("WORK") }) return DataType.TYPE_WORK
  if (tokens.any { it.contains("HOME") }) return DataType.TYPE_HOME

  return DataType.TYPE_UNKNOWN
}

/** Нормализуем телефон для сравнения (убираем пробелы, скобки, + и т.д.) */
private fun normalizePhone(phone: String?): String {
  if (phone.isNullOrBlank()) return ""
  return phone.replace(Regex("[^0-9]"), "")
}

/** Нормализация произвольного адреса/емейла для поиска совпадений */
private fun normalizeForMatch(s: String?): String {
  return s?.lowercase(Locale.ROOT)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
}

/* ---------------------- mailto / MATMSG парсинг ---------------------- */

private data class MailToMeta(val to: String?, val subject: String?, val body: String?)

private fun parseMailTo(raw: String?): MailToMeta? {
  if (raw.isNullOrBlank()) return null
  // mailto:
  if (raw.startsWith("mailto:", ignoreCase = true)) {
    return try {
      val uri = raw.toUri()
      val to = uri.schemeSpecificPart?.substringBefore('?') ?: ""
      val q = uri.query
      val params = q?.split('&')?.mapNotNull {
        val kv = it.split('=')
        if (kv.isEmpty()) null else kv[0] to (kv.getOrNull(1) ?: "")
      }?.toMap().orEmpty()
      val subject = params["subject"]?.let { Uri.decode(it) } ?: ""
      val body = params["body"]?.let { Uri.decode(it) } ?: ""
      MailToMeta(to = to, subject = subject, body = body)
    } catch (_: Exception) {
      null
    }
  }

  // MATMSG:TO:foo@bar.com;SUB:Hello;BODY:World;;
  if (raw.startsWith("MATMSG:", ignoreCase = true)) {
    val to = Regex("TO:([^;]+)").find(raw)?.groupValues?.get(1)
    val subject = Regex("SUB:([^;]+)").find(raw)?.groupValues?.get(1)
    val body = Regex("BODY:([^;]+)").find(raw)?.groupValues?.get(1)
    return MailToMeta(to = to, subject = subject, body = body)
  }

  return null
}

private fun extractCalendarStatus(raw: String): String {
  return raw
    .lineSequence()
    .firstOrNull { it.startsWith("STATUS:", ignoreCase = true) }
    ?.substringAfter("STATUS:", "")
    ?.trim()
    .orEmpty()
}

private fun Long?.toParcelableCalendarEvent(): CalendarDateTimeParcelable {
  if (this == null || this <= 0L) {
    return CalendarDateTimeParcelable(
      day = -1,
      hours = -1,
      minutes = -1,
      month = -1,
      seconds = -1,
      year = -1,
      utc = false
    )
  }

  val cal = Calendar.getInstance().apply { timeInMillis = this@toParcelableCalendarEvent }
  return CalendarDateTimeParcelable(
    day = cal.get(Calendar.DAY_OF_MONTH),
    hours = cal.get(Calendar.HOUR_OF_DAY),
    minutes = cal.get(Calendar.MINUTE),
    month = cal.get(Calendar.MONTH) + 1,
    seconds = cal.get(Calendar.SECOND),
    year = cal.get(Calendar.YEAR),
    utc = cal.timeZone.rawOffset == 0
  )
}