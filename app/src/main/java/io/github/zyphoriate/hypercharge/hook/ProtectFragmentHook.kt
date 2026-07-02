package io.github.zyphoriate.hypercharge.hook

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import io.github.zyphoriate.hypercharge.ui.ChargeValueDialogContent
import io.github.zyphoriate.hypercharge.utils.ChargeProtectionUtils
import io.github.zyphoriate.hypercharge.utils.RemoteEventHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method

/**
 * Hook logic for ChargeProtectFragment in MIUI Security Center.
 *
 * Uses libxposed API 101 interceptor-chain pattern:
 *   chain.proceed() → original method executes
 *   code after proceed() → "after" hook behavior
 *   code before proceed() → "before" hook behavior
 *
 * @author qingyu
 */
object ProtectFragmentHook {

    private const val PREFERENCE_KEY_INTELLECT_PROTECT = "cb_intellect_charge_protect"
    private const val PREFERENCE_KEY_CATEGORY_PROTECT = "category_features_battery_protect"
    private const val PREFERENCE_KEY_SMART_CHARGE_VALUE_SET = "charge_protect_value_setting"

    private var appContext: Context? = null

    fun apply(
        xposedInterface: XposedInterface,
        onCreatePrefsMethod: Method,
        onPreferenceClickMethod: Method,
        getPreferenceScreenMethod: Method,
        findPreferenceMethod: Method,
        requireContextMethod: Method,
        fragmentClass: Class<*>,
        packageName: String,
    ) {
        // Hook onCreatePreferences: inject custom preference after original execution
        xposedInterface.hook(onCreatePrefsMethod).intercept(object : Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val fragment = chain.thisObject
                val args = chain.args

                // Execute original onCreatePreferences first
                val result = chain.proceed()

                try {
                    appContext = requireContextMethod.invoke(fragment) as? Context

                    if (isSmartChargeAvailable(fragment, findPreferenceMethod)) {
                        // Add our custom preference to the existing screen
                        addSmartChargePreference(
                            fragment = fragment,
                            getPreferenceScreenMethod = getPreferenceScreenMethod,
                            findPreferenceMethod = findPreferenceMethod,
                            requireContextMethod = requireContextMethod,
                        )
                    } else {
                        // Smart charge not available — tell remote process to cancel
                        appContext?.let { ctx ->
                            RemoteEventHelper.sendEvent(ctx, RemoteEventHelper.Event.UnregisterBatteryReceiver)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HyperSmartCharge", "onCreatePreferences hook error", e)
                }

                return result
            }
        })

        // Hook onPreferenceClick: intercept clicks on our custom preference
        xposedInterface.hook(onPreferenceClickMethod).intercept(object : Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val fragment = chain.thisObject
                val preference = chain.args[0]

                try {
                    // Check if the clicked preference is ours
                    val prefKey = preference?.javaClass?.getMethod("getKey")?.invoke(preference) as? String

                    if (prefKey == PREFERENCE_KEY_SMART_CHARGE_VALUE_SET) {
                        val context = appContext ?: requireContextMethod.invoke(fragment) as? Context
                        context?.let { ctx ->
                            showChargeValueDialog(ctx, preference)
                        }
                        return true // handled
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HyperSmartCharge", "onPreferenceClick hook error", e)
                }

                // Not our preference — proceed with original handler
                return chain.proceed()
            }
        })
    }

    /**
     * Show the miuix Compose-based dialog for setting charge cutoff value.
     */
    private fun showChargeValueDialog(context: Context, preference: Any) {
        val composeView = ComposeView(context)

        val dialog = AlertDialog.Builder(context)
            .setView(composeView)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .create()

        composeView.setContent {
            ChargeValueDialogContent(
                context = context,
                onConfirm = { value ->
                    // Apply the charge protection setting
                    val success: Boolean
                    val percentValue: Int?
                    if (value != null) {
                        success = ChargeProtectionUtils.openCommonProtectMode(value)
                        percentValue = value
                    } else {
                        success = ChargeProtectionUtils.closeSmartCharge()
                        percentValue = null
                    }

                    // Persist the user choice
                    ChargeProtectionUtils.putSmartChargePercentValue(context, percentValue)

                    // Update the preference text
                    try {
                        val text = getSmartChargeValueText(context, percentValue)
                        preference.javaClass.getMethod("setText", String::class.java)
                            .invoke(preference, text)
                    } catch (_: Exception) {}

                    // Notify remote process about the change
                    val notifValue = if (success) percentValue?.toString() else null
                    RemoteEventHelper.sendEvent(
                        context,
                        RemoteEventHelper.Event.UpdateNotification(notifValue)
                    )

                    // Show result toast
                    val msg = if (success) {
                        if (percentValue != null) "$percentValue%" else "Closed"
                    } else {
                        "Failed"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                    dialog.dismiss()
                },
                onCancel = { dialog.dismiss() }
            )
        }

        dialog.show()
    }

    private fun addSmartChargePreference(
        fragment: Any,
        getPreferenceScreenMethod: Method,
        findPreferenceMethod: Method,
        requireContextMethod: Method,
    ) {
        val context = requireContextMethod.invoke(fragment) as Context

        // Get the PreferenceScreen
        val preferenceScreen = getPreferenceScreenMethod.invoke(fragment)

        // Create a new PreferenceCategory for our items
        val preferenceCategoryClass = Class.forName("miuix.preference.PreferenceCategory")
        val catConstructor = preferenceCategoryClass.getConstructor(Context::class.java, android.util.AttributeSet::class.java)
        val category = catConstructor.newInstance(context, null)

        // Add the category to the screen
        preferenceScreen.javaClass.getMethod("addPreference", Class.forName("androidx.preference.Preference"))
            .invoke(preferenceScreen, category)

        // Create our TextPreference
        val textPrefClass = Class.forName("miuix.preference.TextPreference")
        val textPrefConstructor = textPrefClass.getConstructor(Context::class.java)
        val textPref = textPrefConstructor.newInstance(context)

        // Configure the preference
        textPrefClass.getMethod("setOnPreferenceClickListener", Class.forName("androidx.preference.Preference.OnPreferenceClickListener"))
            .invoke(textPref, fragment)
        textPrefClass.getMethod("setKey", String::class.java)
            .invoke(textPref, PREFERENCE_KEY_SMART_CHARGE_VALUE_SET)
        textPrefClass.getMethod("setEnabled", Boolean::class.java)
            .invoke(textPref, true)

        // Set the title from app resources
        val title = getModuleString(context, "app_name", "HyperSmartCharge")
        textPrefClass.getMethod("setTitle", CharSequence::class.java).invoke(textPref, title)

        val summary = getModuleString(context, "smart_charge_pref_summary", "")
        textPrefClass.getMethod("setSummary", CharSequence::class.java).invoke(textPref, summary)

        // Set current value text
        val valueText = getSmartChargeValueText(context)
        textPrefClass.getMethod("setText", String::class.java).invoke(textPref, valueText)

        // Add to the category
        preferenceCategoryClass.getMethod("addPreference", Class.forName("androidx.preference.Preference"))
            .invoke(category, textPref)
    }

    private fun getSmartChargeValueText(context: Context, value: Int? = null): String {
        val percent = value ?: ChargeProtectionUtils.getSmartChargePercentValue(context)
        return if (percent != null && ChargeProtectionUtils.isSmartChargePercentValueValid(percent)) {
            "$percent%"
        } else {
            getModuleString(context, "smart_charge_close", "Close")
        }
    }

    private fun isSmartChargeAvailable(fragment: Any, findPreferenceMethod: Method): Boolean {
        return try {
            findPreferenceMethod.invoke(fragment, PREFERENCE_KEY_CATEGORY_PROTECT)?.let { category ->
                findPreferenceMethod.invoke(category, PREFERENCE_KEY_INTELLECT_PROTECT)
            } != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get a string resource from the module's own resources.
     * Since we're running in the host app's context, we need to use the module's package.
     */
    @Suppress("DiscouragedApi")
    private fun getModuleString(context: Context, name: String, fallback: String): String {
        return try {
            val resId = context.resources.getIdentifier(name, "string", "io.github.zyphoriate.hypercharge")
            if (resId != 0) context.resources.getString(resId) else fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
