package com.patrolin.qplayer.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.patrolin.qplayer.appContext

fun showToast(text: String, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(appContext, text, length).show()
}
fun getPermissionsText(type: String): String {
    return "Please enable $type permissions in Settings > Apps > ${getAppName()}."
}
fun hasPermissions(vararg permissions: String): Boolean {
    return permissions.all { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }
}
fun requestPermissions(vararg permissions: String): Boolean {
    val havePermissions = hasPermissions(*permissions)
    if (!havePermissions) {
        ActivityCompat.requestPermissions(appContext, permissions, 1)
    }
    return havePermissions
}
val READ_PERMISSIONS: Array<String> get() {
    return if (getAndroidVersion() < 13) {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    }
}