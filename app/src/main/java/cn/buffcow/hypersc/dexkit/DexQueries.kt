package cn.buffcow.hypersc.dexkit

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
                // References preference key strings
                usingStrings(
                    "cb_intellect_charge_protect",
                    "charge_protect_value_setting"
                )
            }
        }.singleOrNull()
            ?: error("onPreferenceClick not found")
    }

    /**
     * Find getPreferenceScreen() method (returns PreferenceScreen).
     */
    fun findGetPreferenceScreenMethod(bridge: DexKitBridge): MethodData {
        return bridge.findMethod {
            searchPackages("com.miui.powercenter")
            matcher {
                name("getPreferenceScreen")
                paramCount = 0
                returnType("androidx.preference.PreferenceScreen")
            }
        }.firstOrNull()
            ?: bridge.findMethod {
                searchPackages("com.miui.powercenter")
                matcher {
                    name("getPreferenceScreen")
                    paramCount = 0
                }
            }.firstOrNull()
            ?: error("getPreferenceScreen not found")
    }

    /**
     * Find findPreference(CharSequence): Preference method.
     */
    fun findFindPreferenceMethod(bridge: DexKitBridge): MethodData {
        return bridge.findMethod {
            searchPackages("com.miui.powercenter")
            matcher {
                name("findPreference")
                paramTypes("java.lang.CharSequence")
            }
        }.firstOrNull()
            ?: error("findPreference not found")
    }

    /**
     * Find requireContext(): Context method (inherited from Fragment).
     */
    fun findRequireContextMethod(bridge: DexKitBridge): MethodData {
        return bridge.findMethod {
            searchPackages("androidx.fragment.app")
            matcher {
                name("requireContext")
                paramCount = 0
                returnType("android.content.Context")
            }
        }.firstOrNull()
            ?: bridge.findMethod {
                searchPackages("android.app")
                matcher {
                    name("requireContext")
                    paramCount = 0
                }
            }.firstOrNull()
            ?: error("requireContext method not found")
    }
}
