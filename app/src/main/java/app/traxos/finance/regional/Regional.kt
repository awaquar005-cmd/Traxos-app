package app.traxos.finance.regional

import android.content.Context
import androidx.room.*
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

@Entity(
    tableName = "manual_rates",
    indices = [Index(value = ["source", "target", "day"], unique = true)],
)
data class ManualRate(
    @PrimaryKey val id: String,
    val source: String,
    val target: String,
    val day: Long,
    val rate: String,
    val revision: Long = 1,
)

@Dao
interface RegionalDao {
    @Query("SELECT * FROM manual_rates ORDER BY day DESC,source,target")
    fun rates(): Flow<List<ManualRate>>

    @Query("SELECT * FROM manual_rates WHERE id=:id") suspend fun rate(id: String): ManualRate?

    @Query("SELECT COUNT(*) FROM manual_rates") suspend fun count(): Long

    @Insert suspend fun insert(row: ManualRate)

    @Update suspend fun update(row: ManualRate)

    @Query("DELETE FROM manual_rates WHERE id=:id AND revision=:revision")
    suspend fun delete(id: String, revision: Long): Int
}

object ExchangeRules {
    fun validate(row: ManualRate, allowFuture: Boolean = false) {
        UUID.fromString(row.id)
        require(row.revision >= 1) { "Invalid rate revision" }
        require(
            row.source in Money.currencies &&
                row.target in Money.currencies &&
                row.source != row.target
        ) {
            "Choose two different supported currencies"
        }
        require(
            row.day in
                LocalDate.of(1900, 1, 1).toEpochDay()..(if (allowFuture)
                        LocalDate.of(2200, 12, 31).toEpochDay()
                    else LocalDate.now().toEpochDay())
        ) {
            "Rate date must be between1900 and today"
        }
        require(row.rate.matches(Regex("[0-9]{1,12}(\\.[0-9]{1,12})?"))) {
            "Enter a positive decimal rate, up to12 decimal places"
        }
        require(
            BigDecimal(row.rate) > BigDecimal.ZERO &&
                BigDecimal(row.rate) <= BigDecimal("999999999999")
        ) {
            "Rate is outside the supported range"
        }
    }

    data class Converted(val minor: Long, val rate: ManualRate, val inverse: Boolean)

    fun convert(
        minor: Long,
        source: String,
        target: String,
        day: Long,
        rates: List<ManualRate>,
        inverse: Boolean = false,
        rounding: String = "HALF_EVEN",
    ): Converted? {
        require(minor in -Money.MAX_MINOR..Money.MAX_MINOR) {
            "Source amount is outside the supported range"
        }
        require(source in Money.currencies && target in Money.currencies)
        require(rounding in listOf("HALF_EVEN", "HALF_UP"))
        if (source == target) return null
        val direct =
            rates
                .filter { it.source == source && it.target == target && it.day <= day }
                .maxByOrNull { it.day }
        val selected =
            direct
                ?: if (inverse)
                    rates
                        .filter { it.target == source && it.source == target && it.day <= day }
                        .maxByOrNull { it.day }
                else null
        selected ?: return null
        validate(selected, allowFuture = true)
        val amount = BigDecimal.valueOf(minor, Currency.getInstance(source).defaultFractionDigits)
        val scale = Currency.getInstance(target).defaultFractionDigits
        val mode = RoundingMode.valueOf(rounding)
        val out =
            try {
                val rounded =
                    if (direct != null)
                        amount.multiply(BigDecimal(selected.rate)).setScale(scale, mode)
                    else amount.divide(BigDecimal(selected.rate), scale, mode)
                rounded.movePointRight(scale).longValueExact()
            } catch (e: ArithmeticException) {
                throw IllegalArgumentException("Converted amount is too large")
            }
        require(out in -Money.MAX_MINOR..Money.MAX_MINOR) {
            "Converted amount is outside the supported range"
        }
        return Converted(out, selected, direct == null)
    }
}

class RegionalRepository(private val db: TraxosDatabase) {
    val dao = db.regional()

    suspend fun save(row: ManualRate, expected: Long) =
        withContext(Dispatchers.IO) {
            ExchangeRules.validate(row)
            db.withTransaction {
                val old = dao.rate(row.id)
                require(old != null || dao.count() < 10000) {
                    "Supported manual rate limit reached; remove an unused rate first"
                }
                if (old == row) return@withTransaction
                require((old?.revision ?: 0) == expected) { "Rate changed; reopen it" }
                val next =
                    row.copy(
                        rate = BigDecimal(row.rate).stripTrailingZeros().toPlainString(),
                        revision = expected + 1,
                    )
                if (old == null) dao.insert(next) else dao.update(next)
            }
        }

    suspend fun delete(row: ManualRate) =
        withContext(Dispatchers.IO) {
            require(dao.delete(row.id, row.revision) == 1) { "Rate changed; reopen it" }
        }
}

data class DisplayOptions(
    val locale: String = "SYSTEM",
    val date: String = "d MMM yyyy",
    val zone: String = "SYSTEM",
    val clock: String = "24",
    val currencyCode: Boolean = false,
    val textScale: String = "1.0",
    val contrast: Boolean = false,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val inverse: Boolean = false,
    val rounding: String = "HALF_EVEN",
    val launcher: String = "TRAXOS",
) {
    fun localeValue(): Locale =
        if (locale == "SYSTEM") Locale.getDefault() else Locale.forLanguageTag(locale)

    fun zoneValue(): ZoneId = if (zone == "SYSTEM") ZoneId.systemDefault() else ZoneId.of(zone)

    fun date(day: Long): String =
        LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(date, localeValue()))

    fun time(instant: Instant): String =
        instant
            .atZone(zoneValue())
            .format(
                DateTimeFormatter.ofPattern(if (clock == "12") "h:mm a" else "HH:mm", localeValue())
            )

    fun validate() {
        require(locale in locales.map { it.first })
        require(date in dates)
        require(zone == "SYSTEM" || zone in ZoneId.getAvailableZoneIds())
        require(clock in listOf("12", "24"))
        require(textScale in listOf("1.0", "1.1", "1.2", "1.3"))
        require(rounding in listOf("HALF_EVEN", "HALF_UP"))
        require(launcher in listOf("TRAXOS", "FINANCE"))
    }

    companion object {
        val locales =
            listOf(
                "SYSTEM" to "Device language/region",
                "en-IN" to "English · India",
                "hi-IN" to "हिन्दी · भारत",
                "ur-IN" to "اردو · بھارت",
                "en-US" to "English · US",
                "en-GB" to "English · UK",
            )
        val dates = listOf("d MMM yyyy", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd")
    }
}

class DeviceOptions(context: Context) {
    private val prefs = context.getSharedPreferences("display_options", 0)
    val state = MutableStateFlow(read())

    private fun read(): DisplayOptions =
        runCatching {
                DisplayOptions(
                        prefs.getString("locale", "SYSTEM")!!,
                        prefs.getString("date", "d MMM yyyy")!!,
                        prefs.getString("zone", "SYSTEM")!!,
                        prefs.getString("clock", "24")!!,
                        prefs.getBoolean("currencyCode", false),
                        prefs.getString("textScale", "1.0")!!,
                        prefs.getBoolean("contrast", false),
                        prefs.getBoolean("haptics", true),
                        prefs.getBoolean("reduceMotion", false),
                        prefs.getBoolean("inverse", false),
                        prefs.getString("rounding", "HALF_EVEN")!!,
                        prefs.getString("launcher", "TRAXOS")!!,
                    )
                    .also { it.validate() }
            }
            .getOrDefault(DisplayOptions())

    suspend fun save(value: DisplayOptions) =
        withContext(Dispatchers.IO) {
            value.validate()
            check(
                prefs
                    .edit()
                    .putString("locale", value.locale)
                    .putString("date", value.date)
                    .putString("zone", value.zone)
                    .putString("clock", value.clock)
                    .putBoolean("currencyCode", value.currencyCode)
                    .putString("textScale", value.textScale)
                    .putBoolean("contrast", value.contrast)
                    .putBoolean("haptics", value.haptics)
                    .putBoolean("reduceMotion", value.reduceMotion)
                    .putBoolean("inverse", value.inverse)
                    .putString("rounding", value.rounding)
                    .putString("launcher", value.launcher)
                    .commit()
            ) {
                "Could not save display settings"
            }
            state.value = value
        }
}
