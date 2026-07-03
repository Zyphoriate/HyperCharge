package io.github.zyphoriate.hypercharge.dexkit

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData

object DexQueries {

    fun findChargeProtectFragment(bridge: DexKitBridge): ClassData {
        return bridge.findClass {
            searchPackages("com.miui.powercenter")
            matcher {
                usingStrings(
                    "cb_intellect_charge_protect",
                    "category_features_battery_protect"
                )
                methods {
                    add {
                        name("onCreatePreferences")
                        paramTypes("android.os.Bundle", "java.lang.String")
                    }
                }
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

    fun findOnCreatePreferencesMethod(bridge: DexKitBridge): MethodData {
        return bridge.findMethod {
            searchPackages("com.miui.powercenter")
            matcher {
                name("onCreatePreferences")
                paramTypes("android.os.Bundle", "java.lang.String")
                usingStrings(
                    "cb_intellect_charge_protect",
                    "category_features_battery_protect"
                )
            }
        }.singleOrNull()
            ?: error("onCreatePreferences not found")
    }
}
