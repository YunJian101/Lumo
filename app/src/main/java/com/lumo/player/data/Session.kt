package com.lumo.player.data

import android.content.Context

object Session {
    private const val PREF = "lumo_prefs"
    private const val KEY_HOST = "host"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"

    fun host(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_HOST, "") ?: ""

    fun token(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "") ?: ""

    fun user(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_USER, "") ?: ""

    fun save(context: Context, host: String, token: String, user: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOST, host)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, user)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TOKEN)
            .apply()
    }
}
