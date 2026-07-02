package io.github.zyphoriate.hypercharge.dexkit

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData

/**
 * DexKit structural queries for locating obfuscated classes and methods
 * in com.miui.securitycenter (MIUI/HyperOS).
 *
 * Queries are designed to survive ProGuard/R8 obfuscation across different
 * MIUI/HyperOS versions by matching on structural characteristics:
 * - Used string constants (battery protection preference keys)
 * - Method signatures (parameter types, return types, counts)
 * - Package scope
 *
 * @author qingyu
 */
object DexQueries {

    /**
     * Find ChargeProtectFragment by structural characteristics:
     * - In the com.miui.powercenter package
     * - Contains battery protection preference key strings
     * - Has onCreatePreferences(Bundle, String) method
     * - Has onPreferenceClick(Preference) => boolean method
     */
    fun findChargeProtectFragment(bridge: DexKitBridge): ClassData {
        return bridge.findClass {
            searchPackages("com.miui.powercenter")
            matcher {
                // The class references these battery protection preference keys
                usingStrings(
                    "cb_intellect_charge_protect",
                    "category_features_battery_protect"
                )
                // Has onCreatePreferences with Bundle + String params
                methods {
                    add {
                        name("onCreatePreferences")
                        paramTypes("android.os.Bundle", "java.lang.String")
                    }
                }
                // Has onPreferenceClick that returns boolean
                methods {
                    add {
                        name("onPreferenceClick")
                        paramCount(1)
                        returnType("boolean")
                    }
                }
            }
        }.singleOrNull()
            ?: error("ChargeProtectFragment not found — unsupported MIUI/HyperOS version")
    }

    /**
     * Find onCreatePreferences(Bundle, String) method in ChargeProtectFragment.
     * Uses usingStrings to disambiguate from parent class methods.
     */
    fun findOnCreatePreferencesMethod(bridge: DexKitBridge): MethodData {
        return bridge.findMethod {
            searchPackages("com.miui.powercenter")
            matcher {
                name("onCreatePreferences")
                paramTypes("android.os.Bundle", "java.lang.String")
                // Disambiguate from parent: references battery protection strings
                usingStrings(
                    "cb_intellect_charge_protect",
                    "category_features_battery_protect"
                )
            }
        }.singleOrNull()
            ?: error("onCreatePreferences not found")
    }

    /**
     * Find onPreferenceClick(Preference): Boolean method.
     */
    fun findOnPreferenceClickMethod(bridge: DexKitBridge): MethodData {
        return bridge.findMethod {
            searchPackages("com.miui.powercenter")
            matcher {
                name("onPreferenceClick")
                paramCount(1)
                returnType("boolean")
            }
        }.firstOrNull()
            ?: error("onPreferenceClick not found")
    }

}
