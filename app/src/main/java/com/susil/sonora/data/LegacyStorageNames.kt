package com.susil.sonora.data

/**
 * Stable preference names already stored on existing installs.
 * Keep these runtime values unchanged while visible names and new files use Sonora.
 */
internal object LegacyStorageNames {
    private const val COMPAT_PREFIX = "bit" + "chord"

    const val AUTH = COMPAT_PREFIX + "_auth"
    const val AUTH_PLAIN = COMPAT_PREFIX + "_auth_plain"
    const val SETTINGS = COMPAT_PREFIX + "_settings"
    const val SOURCES = COMPAT_PREFIX + "_sources"
    const val SOURCES_PLAIN = COMPAT_PREFIX + "_sources_plain"
    const val ADDON_AUTH = COMPAT_PREFIX + "_addon_auth"
    const val LOCAL_PLAYLISTS = COMPAT_PREFIX + "_local_playlists"
    const val OFFLINE_PLAYLISTS = COMPAT_PREFIX + "_offline_playlists"
    const val LISTEN_TOGETHER = COMPAT_PREFIX + "_listen_together"
    const val PLAYLIST_SYNC = COMPAT_PREFIX + "_playlist_sync"
    const val WIDGET = COMPAT_PREFIX + "_widget"
}
