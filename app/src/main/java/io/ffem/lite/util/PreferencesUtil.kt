package io.ffem.lite.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager

/**
 * Various utility functions to get/set values from/to SharedPreferences.
 */
object PreferencesUtil {

    /**
     * Gets a preference key from strings
     *
     * @param context the context
     * @param keyId   the key id
     * @return the string key
     */
    private fun getKey(context: Context, @StringRes keyId: Int): String {
        return context.getString(keyId)
    }

    /**
     * Gets a boolean value from preferences.
     *
     * @param context      the context
     * @param keyId        the key id
     * @param defaultValue the default value
     * @return the stored boolean value
     */
    fun getBoolean(context: Context, @StringRes keyId: Int, defaultValue: Boolean): Boolean {
        return getBoolean(context, getKey(context, keyId), defaultValue)
    }

    fun getBoolean(context: Context, keyId: String, defaultValue: Boolean): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getBoolean(keyId, defaultValue)
    }

    /**
     * Sets a boolean value to preferences.
     *
     * @param context the context
     * @param keyId   the key id
     * @param value   the value
     */
    fun setBoolean(context: Context, @StringRes keyId: Int, value: Boolean) {
        setBoolean(context, getKey(context, keyId), value)
    }

    fun setBoolean(context: Context, keyId: String?, value: Boolean) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putBoolean(keyId, value)
        editor.apply()
    }

    /**
     * Gets a string value from preferences.
     *
     * @param context      the context
     * @param keyId        the key id
     * @param defaultValue default value
     * @return the stored string value
     */
    fun getString(context: Context, @StringRes keyId: Int, defaultValue: String): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(getKey(context, keyId), defaultValue)!!
    }

    @JvmStatic
    fun getString(context: Context?, keyId: String?, defaultValue: String?): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(keyId, defaultValue)
    }

    /**
     * Sets a string value to preferences.
     *
     * @param context the context
     * @param keyId   the key id
     */
    fun setString(context: Context, @StringRes keyId: Int, value: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putString(getKey(context, keyId), value)
        editor.apply()
    }

    fun setString(context: Context?, keyId: String?, value: String?) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putString(keyId, value)
        editor.apply()
    }

    fun getLong(context: Context, @StringRes keyId: Int): Long {
        return getLong(context, getKey(context, keyId))
    }

    private fun getLong(context: Context, key: String): Long {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getLong(key, -1L)
    }

    fun setLong(context: Context, @StringRes keyId: Int, value: Long) {
        setLong(context, getKey(context, keyId), value)
    }

    private fun setLong(context: Context, key: String, value: Long) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putLong(key, value)
        editor.apply()
    }

    /**
     * Removes the key from the preferences.
     *
     * @param context the context
     * @param key     the key id
     */
    fun removeKey(context: Context, key: String) {
        val sharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.remove(key)
        editor.apply()
    }
}
