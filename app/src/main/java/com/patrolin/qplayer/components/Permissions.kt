package com.patrolin.qplayer.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.patrolin.qplayer.appContext

fun openURL(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    ContextCompat.startActivity(appContext, intent, null)
}
fun showToast(text: String, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(appContext, text, length).show()
}
fun getPermissionsText(type: String): String {
    return "Please enable $type permissions in Settings > Apps > ${getAppName()}."
}
fun hasPermissions(vararg permissions: String): Boolean {
    return permissions.all { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }
}

var permissionRequestNonce = 0
typealias PermissionRequestCallback = () -> Unit
var ongoingPermissionRequests = hashMapOf<Int, PermissionRequestCallback>()
fun onPermissionChange(requestCode: Int) {
    val lambda = ongoingPermissionRequests[requestCode]
    lambda?.invoke()
    ongoingPermissionRequests.remove(requestCode)
}
fun requestPermissions(vararg permissions: String, callback: PermissionRequestCallback): Boolean {
    val hadPermissions = hasPermissions(*permissions)
    if (!hadPermissions) {
        //errPrint("Requesting permissions: $permissionRequestNonce, ${permissions.toList()}")
        ongoingPermissionRequests[permissionRequestNonce] = callback
        ActivityCompat.requestPermissions(appContext, permissions, permissionRequestNonce)
    }
    return hadPermissions
}
val READ_PERMISSIONS: Array<String> @SuppressLint("NewApi") get() {
    return if (getAndroidAPIVersion() < 33) {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    }
}