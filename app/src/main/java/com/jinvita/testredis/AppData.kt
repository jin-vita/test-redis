package com.jinvita.testredis

import android.content.Context
import android.util.Log
import android.widget.Toast

object AppData {
    const val ACTION_REDIS_DATA = "com.jinvita.redis.data"
    private var isDebug = true
    fun debug(tag: String, msg: String) {
        if (isDebug) Log.d(tag, msg)
    }

    fun error(tag: String, msg: String) {
        if (isDebug) Log.e(tag, msg)
    }

    fun error(tag: String, msg: String, ex: Exception) {
        if (isDebug) Log.e(tag, msg, ex)
    }

    private lateinit var toast: Toast
    fun showToast(context: Context, msg: String) {
        if (::toast.isInitialized) toast.cancel()
        toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
        toast.show()
    }
}