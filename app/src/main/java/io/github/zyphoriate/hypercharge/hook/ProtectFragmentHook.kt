package io.github.zyphoriate.hypercharge.hook

import android.content.Context
import android.util.Log
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.zyphoriate.hypercharge.BuildConfig
import io.github.zyphoriate.hypercharge.utils.ChargeProtectionUtils
import io.github.zyphoriate.hypercharge.utils.RemoteEventHelper
import java.lang.reflect.Method

object ProtectFragmentHook {

    private const val TAG = "HyperSmartCharge"
    private const val PREFERENCE_KEY_INTELLECT_PROTECT = "cb_intellect_charge_protect"
    private const val PREFERENCE_KEY_CATEGORY_PROTECT = "category_features_battery_protect"
    private const val PREFERENCE_KEY_SMART_CHARGE_VALUE_SET = "charge_protect_value_setting"

    private var appContext: Context? = null

    fun apply(
        xposedInterface: XposedInterface,
        onCreatePrefsMethod: Method,
        onPreferenceClickMethod: Method,
        getPreferenceScreenMethod: Method,
        requireContextMethod: Method,
        @Suppress("UNUSED_PARAMETER") packageName: String,
    ) {
        Log.i(TAG, "Hooking onCreatePreferences in $packageName")

        xposedInterface.hook(onCreatePrefsMethod).intercept { chain ->
            val fragment = chain.thisObject
            Log.d(TAG, "onCreatePreferences intercepted, fragment=$fragment")
            val result = chain.proceed()

            try {
                appContext = requireContextMethod.invoke(fragment) as? Context
                val ctx = appContext
                if (ctx == null) {
                    Log.w(TAG, "Cannot get context from fragment")
                } else if (isSmartChargeAvailable(fragment)) {
                    Log.i(TAG, "Smart charge available — injecting custom preference")
                    addSmartChargePreference(fragment, getPreferenceScreenMethod, requireContextMethod)
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(ctx, "HyperSmartCharge hooked OK", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "Smart charge not available")
                    RemoteEventHelper.sendEvent(ctx, RemoteEventHelper.Event.UnregisterBatteryReceiver)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onCreatePreferences hook error", e)
            }

            result
        }

        Log.i(TAG, "Hooking onPreferenceClick")
        xposedInterface.hook(onPreferenceClickMethod).intercept { chain ->
            val fragment = chain.thisObject
            val preference = chain.args[0]
            Log.d(TAG, "onPreferenceClick intercepted, preference=$preference")

            try {
                val prefKey = preference?.javaClass?.getMethod("getKey")?.invoke(preference) as? String
                Log.d(TAG, "Clicked preference key: $prefKey")
                if (prefKey == PREFERENCE_KEY_SMART_CHARGE_VALUE_SET) {
                    val ctx = appContext ?: requireContextMethod.invoke(fragment) as? Context
                    if (ctx != null) launchSettingsActivity(ctx)
                    return@intercept true
                }
            } catch (e: Exception) {
                Log.e(TAG, "onPreferenceClick hook error", e)
            }

            chain.proceed()
        }

        Log.i(TAG, "HyperSmartCharge hooks installed successfully")
    }

    private fun launchSettingsActivity(context: Context) {
        Log.d(TAG, "Launching SettingsActivity")
        try {
            context.startActivity(
                android.content.Intent().apply {
                    setClassName(
                        "io.github.zyphoriate.hypercharge",
                        "io.github.zyphoriate.hypercharge.ui.SettingsActivity"
                    )
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SettingsActivity", e)
            Toast.makeText(context, "Launch failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSmartChargePreference(
        fragment: Any,
        getPreferenceScreenMethod: Method,
        requireContextMethod: Method,
    ) {
        val cl = fragment.javaClass.classLoader
        val context = requireContextMethod.invoke(fragment) as Context
        val preferenceScreen = getPreferenceScreenMethod.invoke(fragment)

        val preferenceCategoryClass = cl.loadClass("miuix.preference.PreferenceCategory")
        val catConstructor = preferenceCategoryClass.getConstructor(Context::class.java, android.util.AttributeSet::class.java)
        val category = catConstructor.newInstance(context, null)

        val prefBaseClass = cl.loadClass("androidx.preference.Preference")
        preferenceScreen.javaClass.getMethod("addPreference", prefBaseClass).invoke(preferenceScreen, category)

        val textPrefClass = cl.loadClass("miuix.preference.TextPreference")
        val textPrefConstructor = textPrefClass.getConstructor(Context::class.java)
        val textPref = textPrefConstructor.newInstance(context)

        val setListenerMethod = textPrefClass.methods.find {
            it.name == "setOnPreferenceClickListener" && it.parameterTypes.size == 1
        }
        setListenerMethod?.invoke(textPref, fragment)
        Log.d(TAG, "setOnPreferenceClickListener result: ${setListenerMethod != null}")

        textPrefClass.getMethod("setKey", String::class.java).invoke(textPref, PREFERENCE_KEY_SMART_CHARGE_VALUE_SET)
        textPrefClass.getMethod("setEnabled", Boolean::class.java).invoke(textPref, true)

        val title = getModuleString(context, "app_name", "HyperSmartCharge")
        textPrefClass.getMethod("setTitle", CharSequence::class.java).invoke(textPref, title)
        val summary = getModuleString(context, "smart_charge_pref_summary", "")
        textPrefClass.getMethod("setSummary", CharSequence::class.java).invoke(textPref, summary)
        val valueText = getSmartChargeValueText(context)
        textPrefClass.getMethod("setText", String::class.java).invoke(textPref, valueText)

        preferenceCategoryClass.getMethod("addPreference", prefBaseClass).invoke(category, textPref)
        Log.i(TAG, "Custom preference added successfully")
    }

    private fun getSmartChargeValueText(context: Context, value: Int? = null): String {
        val percent = value ?: ChargeProtectionUtils.getSmartChargePercentValue(context)
        return if (percent != null && ChargeProtectionUtils.isSmartChargePercentValueValid(percent)) "$percent%"
        else getModuleString(context, "smart_charge_close", "Close")
    }

    private fun findPreference(who: Any, key: String): Any? {
        return try {
            var method: Method? = null
            var clazz: Class<*>? = who.javaClass
            while (clazz != null && method == null) {
                try { method = clazz.getDeclaredMethod("findPreference", String::class.java) }
                catch (_: NoSuchMethodException) {
                    try { method = clazz.getDeclaredMethod("findPreference", CharSequence::class.java) }
                    catch (_: NoSuchMethodException) { clazz = clazz.superclass }
                }
            }
            method?.invoke(who, key)
        } catch (e: Exception) {
            Log.e(TAG, "findPreference error on ${who.javaClass.simpleName}", e)
            null
        }
    }

    private fun isSmartChargeAvailable(fragment: Any): Boolean {
        val category = findPreference(fragment, PREFERENCE_KEY_CATEGORY_PROTECT)
        Log.d(TAG, "findPreference(category_features_battery_protect) = $category")
        if (category == null) return false
        val intellect = findPreference(category, PREFERENCE_KEY_INTELLECT_PROTECT)
        Log.d(TAG, "findPreference(cb_intellect_charge_protect) = $intellect")
        return intellect != null
    }

    @Suppress("DiscouragedApi")
    private fun getModuleString(context: Context, name: String, fallback: String): String {
        return try {
            val resId = context.resources.getIdentifier(name, "string", "io.github.zyphoriate.hypercharge")
            if (resId != 0) context.resources.getString(resId) else fallback
        } catch (_: Exception) { fallback }
    }
}
