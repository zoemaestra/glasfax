package com.zoemaestra.fauxmachine

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // ignore
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // We create the activity in full screen to avoid burn in, since the OP Nord is an AMOLED and
        // persistent UI elements would damage it.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Requires obvious perms
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }

        // Requires less obvious perms to try to not get killed to reclaim memory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // Starts anti background killing service
        val serviceIntent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Requests perms to be OS's main SMS app. Useful for, you guessed it, not getting killed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == false) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 101)
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivity(intent)
            }
        }

        // Requests being excluded from bg battery optimisation, aka being killed
        // You'd think this alone would be enough to not get the app killed, but alas, it is not
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        // sharedprefs is used to store user data that persists between sessions
        // In this case, it is to store and load the IP used to refer to the printer server
        val sharedPrefs = getSharedPreferences("FauxMachinePrefs", Context.MODE_PRIVATE)

        setContent {
            var showMessage by remember { mutableStateOf(false) }
            var ipAddress by remember { mutableStateOf(sharedPrefs.getString("printer_ip", "10.0.100.113") ?: "10.0.100.113") }
            var lastInteraction by remember { mutableStateOf(0L) }

            // Hide UI after 10s to preserve the screen
            if (showMessage) {
                LaunchedEffect(lastInteraction) {
                    delay(10000)
                    showMessage = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { 
                        showMessage = true
                        lastInteraction = System.currentTimeMillis()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (showMessage) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "This is the faux machine\n" +
                                    "It sends received SMS messages to the receipt printer like a fake fax machine.\n" +
                                    "Please don't unplug the phone, close the app or lock the screen!\n" +
                                    "This will disappear after 10 seconds :)",
                            color = Color.White,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.padding(32.dp)
                        )
                        
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { 
                                ipAddress = it
                                sharedPrefs.edit().putString("printer_ip", it).apply()
                                lastInteraction = System.currentTimeMillis()
                            },
                            label = { Text("Print Server IP", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}