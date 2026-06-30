package com.zoemaestra.fauxmachine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
// Class for handling receiving SMS messages, formatting them and forwarding to the print server
class SmsReceiver : BroadcastReceiver() {
    private val client = OkHttpClient()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            val sharedPrefs = context.getSharedPreferences("FauxMachinePrefs", Context.MODE_PRIVATE)
            val ipAddress = sharedPrefs.getString("printer_ip", "10.0.100.113") ?: "10.0.100.113"

            for (sms in messages) {
                val messageBody = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress
                Log.d("SmsReceiver", "Received SMS from $sender: $messageBody")

                val receiptText = """
========================================
       >>> NEW TRANSMISSION
       >>> FROM: $sender
========================================

   ___  _     _   ___  ___  _   _    _
  / __|| |   /_\ / __|| __ /_\  \ \/ /
 | (_ || |__/ _ \\__ \| _|/ _ \  >  <
  \___||___/_/ \_\___/|_|/_/ \_\/_/\_\

========================================
             BEGIN MESSAGE
========================================

$messageBody

========================================
             END MESSAGE
========================================
                """.trimIndent()
                sendToPrinter(receiptText, ipAddress)
            }
        }
    }

    // Making the request to the server
    private fun sendToPrinter(text: String, ipAddress: String) {
        Thread {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("receipt_text", text)
                    .addFormDataPart("cut_paper", "on")
                    .build()

                val request = Request.Builder()
                    .url("http://$ipAddress/cgi-bin/print_png.cgi")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d("SmsReceiver", "Printer Response Code: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error sending to printer", e)
            }
        }.start()
    }
}

