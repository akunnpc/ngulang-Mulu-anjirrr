package com.example.util

import android.content.Context
import android.content.SharedPreferences

object CalibrationPrefs {
    private const val PREFS_NAME = "autotap_calibration_prefs"
    
    private const val KEY_GLOBAL_OFFSET_X = "global_offset_x"
    private const val KEY_GLOBAL_OFFSET_Y = "global_offset_y"
    private const val KEY_ORIENTATION_MODE = "orientation_mode"
    private const val KEY_BOUNDS_MARGIN_LEFT = "bounds_margin_left"
    private const val KEY_BOUNDS_MARGIN_RIGHT = "bounds_margin_right"
    private const val KEY_BOUNDS_MARGIN_TOP = "bounds_margin_top"
    private const val KEY_BOUNDS_MARGIN_BOTTOM = "bounds_margin_bottom"

    const val MODE_AUTO = "AUTO"
    const val MODE_LANDSCAPE = "LANDSCAPE"
    const val MODE_PORTRAIT = "PORTRAIT"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getGlobalOffsetX(context: Context): Int {
        return getPrefs(context).getInt(KEY_GLOBAL_OFFSET_X, 0)
    }

    fun setGlobalOffsetX(context: Context, offset: Int) {
        getPrefs(context).edit().putInt(KEY_GLOBAL_OFFSET_X, offset).apply()
    }

    fun getGlobalOffsetY(context: Context): Int {
        return getPrefs(context).getInt(KEY_GLOBAL_OFFSET_Y, 0)
    }

    fun setGlobalOffsetY(context: Context, offset: Int) {
        getPrefs(context).edit().putInt(KEY_GLOBAL_OFFSET_Y, offset).apply()
    }

    fun getOrientationMode(context: Context): String {
        return getPrefs(context).getString(KEY_ORIENTATION_MODE, MODE_AUTO) ?: MODE_AUTO
    }

    fun setOrientationMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_ORIENTATION_MODE, mode).apply()
    }

    fun getMarginLeft(context: Context): Int {
        return getPrefs(context).getInt(KEY_BOUNDS_MARGIN_LEFT, 0)
    }

    fun getMarginRight(context: Context): Int {
        return getPrefs(context).getInt(KEY_BOUNDS_MARGIN_RIGHT, 0)
    }

    fun getMarginTop(context: Context): Int {
        return getPrefs(context).getInt(KEY_BOUNDS_MARGIN_TOP, 0)
    }

    fun getMarginBottom(context: Context): Int {
        return getPrefs(context).getInt(KEY_BOUNDS_MARGIN_BOTTOM, 0)
    }

    fun setMargins(context: Context, left: Int, top: Int, right: Int, bottom: Int) {
        getPrefs(context).edit()
            .putInt(KEY_BOUNDS_MARGIN_LEFT, left)
            .putInt(KEY_BOUNDS_MARGIN_TOP, top)
            .putInt(KEY_BOUNDS_MARGIN_RIGHT, right)
            .putInt(KEY_BOUNDS_MARGIN_BOTTOM, bottom)
            .apply()
    }

    fun reset(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
