package com.susil.sonora

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The seven supplied home-screen icon choices. */
enum class AppIcon(val alias: String, val iconRes: Int, val labelRes: Int, val previewRes: Int? = null) {
    Default("LauncherDefault", R.mipmap.ic_launcher, R.string.app_icon_default),
    LiquidGlass("LauncherGlassLight", R.mipmap.ic_liquid_glass, R.string.app_icon_liquid_glass),
    LiquidGlassDark("LauncherGlassDark", R.mipmap.ic_liquid_glass_dark, R.string.app_icon_liquid_glass_dark),
    NeonSilver("LauncherNeonSilver", R.mipmap.ic_neon_silver, R.string.app_icon_neon_silver),
    NeonCyan("LauncherNeonCyan", R.mipmap.ic_neon_cyan, R.string.app_icon_neon_cyan),
    NeonGreen("LauncherNeonGreen", R.mipmap.ic_neon_green, R.string.app_icon_neon_green),
    Monochrome("LauncherMonochrome", R.mipmap.ic_monochrome, R.string.app_icon_monochrome);

    private fun component(context: Context) =
        ComponentName(context.packageName, "com.susil.sonora.$alias")

    companion object {
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: Default
        }

        /** Enable the new launcher entry before disabling the others. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
