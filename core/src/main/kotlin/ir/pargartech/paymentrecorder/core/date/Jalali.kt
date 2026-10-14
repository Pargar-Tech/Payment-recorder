package ir.pargartech.paymentrecorder.core.date

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Jalali (Solar Hijri) calendar conversion, ported from the jalaali-js algorithm
 * (Borkowski's 2820-year-cycle-free method with known break years).
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {

    fun toLocalDate(): LocalDate {
        val (gy, gm, gd) = d2g(j2d(year, month, day))
        return LocalDate.of(gy, gm, gd)
    }

    /** "1403/07/22" with ASCII digits. */
    fun format(): String = "%04d/%02d/%02d".format(year, month, day)

    companion object {
        val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

        val MONTH_NAMES = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )

        fun of(date: LocalDate): JalaliDate {
            val (jy, jm, jd) = d2j(g2d(date.year, date.monthValue, date.dayOfMonth))
            return JalaliDate(jy, jm, jd)
        }

        fun fromEpochMillis(millis: Long, zone: ZoneId = TEHRAN): JalaliDate =
            of(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())

        fun isLeapYear(jy: Int): Boolean = jalCal(jy).leap == 0

        fun monthLength(jy: Int, jm: Int): Int = when {
            jm <= 6 -> 31
            jm <= 11 -> 30
            isLeapYear(jy) -> 30
            else -> 29
        }

        // --- jalaali-js port. Integer division/mod truncate toward zero, as in the original. ---

        private val BREAKS = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
        )

        private class JalCal(val leap: Int, val gy: Int, val march: Int)

        private fun jalCal(jy: Int): JalCal {
            val gy = jy + 621
            var leapJ = -14
            var jp = BREAKS[0]
            require(jy >= jp && jy < BREAKS.last()) { "Invalid Jalali year $jy" }
            var jump = 0
            for (i in 1 until BREAKS.size) {
                val jm = BREAKS[i]
                jump = jm - jp
                if (jy < jm) break
                leapJ += jump / 33 * 8 + (jump % 33) / 4
                jp = jm
            }
            var n = jy - jp
            leapJ += n / 33 * 8 + ((n % 33) + 3) / 4
            if (jump % 33 == 4 && jump - n == 4) leapJ += 1
            val leapG = gy / 4 - (gy / 100 + 1) * 3 / 4 - 150
            val march = 20 + leapJ - leapG
            if (jump - n < 6) n = n - jump + (jump + 4) / 33 * 33
            var leap = (((n + 1) % 33) - 1) % 4
            if (leap == -1) leap = 4
            return JalCal(leap, gy, march)
        }

        private fun j2d(jy: Int, jm: Int, jd: Int): Int {
            val r = jalCal(jy)
            return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - jm / 7 * (jm - 7) + jd - 1
        }

        private fun d2j(jdn: Int): Triple<Int, Int, Int> {
            val gy = d2g(jdn).first
            var jy = gy - 621
            val r = jalCal(jy)
            val jdn1f = g2d(gy, 3, r.march)
            var k = jdn - jdn1f
            if (k >= 0) {
                if (k <= 185) return Triple(jy, 1 + k / 31, k % 31 + 1)
                k -= 186
            } else {
                jy -= 1
                k += 179
                if (r.leap == 1) k += 1
            }
            return Triple(jy, 7 + k / 30, k % 30 + 1)
        }

        private fun g2d(gy: Int, gm: Int, gd: Int): Int {
            var d = (gy + (gm - 8) / 6 + 100100) * 1461 / 4 + (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
            d = d - (gy + 100100 + (gm - 8) / 6) / 100 * 3 / 4 + 752
            return d
        }

        private fun d2g(jdn: Int): Triple<Int, Int, Int> {
            var j = 4 * jdn + 139361631
            j += (4 * jdn + 183187720) / 146097 * 3 / 4 * 4 - 3908
            val i = (j % 1461) / 4 * 5 + 308
            val gd = (i % 153) / 5 + 1
            val gm = (i / 153) % 12 + 1
            val gy = j / 1461 - 100100 + (8 - gm) / 6
            return Triple(gy, gm, gd)
        }
    }
}

/** A Jalali month, used as the home screen's period. */
data class JalaliMonth(val year: Int, val month: Int) {
    init {
        require(month in 1..12)
    }

    fun next() = if (month == 12) JalaliMonth(year + 1, 1) else JalaliMonth(year, month + 1)

    fun previous() = if (month == 1) JalaliMonth(year - 1, 12) else JalaliMonth(year, month - 1)

    fun startMillis(zone: ZoneId = JalaliDate.TEHRAN): Long =
        JalaliDate(year, month, 1).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** Exclusive end. */
    fun endMillis(zone: ZoneId = JalaliDate.TEHRAN): Long = next().startMillis(zone)

    /** "مهر ۱۴۰۳" */
    fun label(): String = "${JalaliDate.MONTH_NAMES[month - 1]} ${toPersian(year.toString())}"

    companion object {
        fun of(date: JalaliDate) = JalaliMonth(date.year, date.month)

        fun current(nowMillis: Long = System.currentTimeMillis()) = of(JalaliDate.fromEpochMillis(nowMillis))

        private fun toPersian(s: String) = s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
    }
}
