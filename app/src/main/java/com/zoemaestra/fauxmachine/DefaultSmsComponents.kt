package com.zoemaestra.fauxmachine

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.activity.ComponentActivity
// Stubs required for Android to give the option to make this the default SMS app
// None of this is otherwise needed, since it isn't doing anything with MMS
// or sending SMS messages. But it is still needed to try and keep OOS from
// killing the app. thanks oneplus!!!!!!!
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent): IBinder? = null
}

class ComposeSmsActivity : ComponentActivity()
