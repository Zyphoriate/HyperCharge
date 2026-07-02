package cn.buffcow.hypersc.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import miui.util.IMiCharge

/**
 * Core charge protection logic.
 *
 * Communicates with the Xiaomi charging hardware/driver via IMiCharge API
 * by writing encoded values to the kernel sysfs "smart_chg" node.
 *
 * Encoding formula:
 *   Open:  0x{ (value << 16) | 17 }
 *   Close: 0x10
 *
 * Example for 80%: (80 << 16) | 17 = 5242897 = 0x500011
 *   - High 16 bits: battery percentage (80)
 *   - Low 16 bits:  control flags (0x11 = 17)
 *
 * @author qingyu
 */
object ChargeProtectionUtils {

    private const val TAG = "HyperSmartCharge"

    const val MIN_CHARGE_PERCENT_VALUE = 20
    const val MAX_CHARGE_PERCENT_VALUE = 100

    private const val KEY_SMART_CHARGE_PERCENT_VALUE = "smart_charge_percent_value"

    // ---- Public API ----

    /**
     * Close smart charge protection by writing 0x10 to the kernel node.
     */
    fun closeSmartCharge(): Boolean = setSmartChargeValue("0x10")

    /**
     * Open common protect mode with the given battery percentage.
     *
     * @param value Battery percentage (20-100) at which charging stops.
     * @return true if the kernel write succeeded.
     */
    fun openCommonProtectMode(value: Int): Boolean {
        val valueToSet = "0x${((value shl 16) or 17).toString(16)}"
        val res = setSmartChargeValue(valueToSet)
        Log.d(TAG, "openCommonProtectMode: result=$res, setValue=$valueToSet")
        return res
    }

    // ---- Smart charge value read/write ----

    private fun getSmartChargeValue(): String? = try {
        IMiCharge.getInstance().getMiChargePath("smart_chg").also {
            Log.d(TAG, "getSmartChargeValue: $it")
        }
    } catch (th: Throwable) {
        Log.e(TAG, "getSmartChargeValue error", th)
        null
    }

    private fun setSmartChargeValue(value: String): Boolean = try {
        IMiCharge.getInstance().setMiChargePath("smart_chg", value).also {
            Log.d(TAG, "setSmartChargeValue: $value, result=$it")
        }
    } catch (th: Throwable) {
        Log.e(TAG, "setSmartChargeValue error", th)
        false
    }

    // ---- Persistent user value (Settings.System) ----

    /**
     * Get the user's stored charge percent value.
     * Handles recovery if the kernel value was reset (e.g., after reboot).
     */
    fun getSmartChargePercentValue(context: Context): Int? {
        val value = context.getPercentValue() ?: return null
        return when {
            !isSmartChargePercentValueValid(value) -> {
                Log.w(TAG, "smart charge percent value invalid, removing")
                context.putPercentValue(null)
                null
            }

            (getSmartChargeValue()?.toIntOrNull() ?: 1) <= 0 -> {
                val res = openCommonProtectMode(value)
                Log.w(TAG, "smart_chg value changed by system, retry set: $res")
                if (res) value else context.putPercentValue(null)
            }

            else -> value
        }
    }

    /**
     * Persist the user's chosen percent value to Settings.System.
     */
    fun putSmartChargePercentValue(context: Context, value: Int?) {
        context.putPercentValue(value?.takeIf(::isSmartChargePercentValueValid))
    }

    fun isSmartChargePercentValueValid(perValue: Int): Boolean {
        return perValue in MIN_CHARGE_PERCENT_VALUE..MAX_CHARGE_PERCENT_VALUE
    }

    // ---- Private helpers ----

    private fun Context.getPercentValue(): Int? {
        return Settings.System.getString(contentResolver, KEY_SMART_CHARGE_PERCENT_VALUE)?.toIntOrNull()
    }

    private fun Context.putPercentValue(value: Int?): Int? {
        return if (Settings.System.putString(
                contentResolver,
                KEY_SMART_CHARGE_PERCENT_VALUE,
                value?.toString()
            )
        ) value else null
    }
}
