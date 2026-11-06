package com.tom.voicecontrol

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Call requestDefaultAssistantRole(activity) from a user-visible Settings button.
 * Android displays and controls the system consent dialog.
 */
object TomAssistantRole {
    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)
                ?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true

    fun isDefaultAssistant(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

    fun requestDefaultAssistantRole(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val manager = activity.getSystemService(RoleManager::class.java) ?: return
        if (!manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) ||
            manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) return
        activity.startActivityForResult(
            manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
            REQUEST_ASSISTANT_ROLE
        )
    }

    const val REQUEST_ASSISTANT_ROLE = 7401
}
