package com.island.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.system.ErrnoException
import lu.knaff.alain.saf_sftp.R

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
Copyright (C) 2025,2026 Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

object ErrorNotification {
    private var isInitialized = false
    private const val CHANNEL_NAME = "ErrorChannel"

    @Synchronized
    private fun createChannel(ctx: Context) {
        if (isInitialized) return
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(
            CHANNEL_NAME,
            CHANNEL_NAME,
            importance
        )
        val notificationManager =
            ctx.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        isInitialized = true
    }

    @JvmStatic
    fun sendNotification(
        ctx: Context,
        title: String,
        t: Throwable
    ) {
        var t = t
        while (true) {
            val c = t.cause
            if (c == null || c is ErrnoException) break
            t = c
        }

        val message = t.toString()

        createChannel(ctx)
        val notificationBuilder =
            Notification.Builder(ctx, CHANNEL_NAME)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setStyle(
                    Notification.BigTextStyle()
                        .bigText(message)
                )
                .setAutoCancel(true)


        val notificationManager =
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(0, notificationBuilder.build())
    }
}
