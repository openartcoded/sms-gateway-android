package tech.artcoded.smsgateway

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.*
import tech.artcoded.smsgateway.ui.theme.SmsGatewayTheme

const val TOPIC = "sms"

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Sms Gateway"


        setContent {

            val multiplePermissionsState = rememberMultiplePermissionsState(
                listOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.INTERNET,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.RECEIVE_BOOT_COMPLETED,
                )
            )

            SmsGatewayTheme {
                SmsGatewayMainPage(multiplePermissionsState)
            }


        }


    }
}


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SmsGatewayMainPage(
    multiplePermissionsState: MultiplePermissionsState, modifier: Modifier = Modifier
) {
    if (!multiplePermissionsState.allPermissionsGranted) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(15.dp)
        ) {
            Button(onClick = { multiplePermissionsState.launchMultiplePermissionRequest() }) {
                Text("Request permissions")
            }
        }
    } else {
        // useful spaghetti code starts here
        val androidCtx = LocalContext.current
        val prefs = Utils.createEncryptedSharedPrefDestructively(androidCtx)
        var mqttService by remember {
            mutableStateOf(Intent(androidCtx, MqttForegroundService::class.java))
        }
        mqttService.action = CHECK_STARTED_MQTT_SERVICE_ACTION
        var endpoint by remember {
            mutableStateOf(prefs.getString("endpoint", "")!!)
        }
        var username by remember { mutableStateOf(prefs.getString("username", "")!!) }
        var password by remember { mutableStateOf(prefs.getString("password", "")!!) }
        var passwordVisible by remember { mutableStateOf(false) }
        var started by remember { mutableStateOf(prefs.getBoolean("isConnected", false)) }
        var logTraces by remember {
            mutableStateOf(
                if (started) {
                    "Started"
                } else "Not started..."
            )
        }
        val coroutineScope = rememberCoroutineScope()
        val startStopToggle: () -> Unit = {
            coroutineScope.launch {
                started = !started
                logTraces += if (started) "\nStarted..." else {
                    "\nStopped..."
                }
                if (!started) {
                    mqttService = Intent(androidCtx, MqttForegroundService::class.java)
                    mqttService.action = STOP_MQTT_SERVICE_ACTION
                    androidCtx.startForegroundService(mqttService)
                } else {
                    with(prefs.edit()) {
                        putString("username", username)
                        putString("password", password)
                        putString("endpoint", endpoint)
                        commit()
                    }
                    mqttService = Intent(androidCtx, MqttForegroundService::class.java)
                    mqttService.action = START_MQTT_SERVICE_ACTION
                    androidCtx.startForegroundService(mqttService)
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(15.dp))
                TextField(value = endpoint,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !started,
                    label = { Text(text = "Endpoint") },
                    onValueChange = { newText ->
                        endpoint = newText.trim()
                    })
                Spacer(modifier = Modifier.height(5.dp))
                TextField(value = username,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !started,
                    label = { Text(text = "Username") },
                    onValueChange = { newText ->
                        username = newText.trim()
                    })
                Spacer(modifier = Modifier.height(5.dp))
                TextField(value = password,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !started,
                    label = { Text(text = "Password") },
                    onValueChange = { newText ->
                        password = newText
                    },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        // Please provide localized description for accessibility services
                        val description =
                            if (passwordVisible) "Hide password" else "Show password"

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, description)
                        }
                    })
                Button(
                    onClick = startStopToggle,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        if (started) "Stop" else "Start", modifier = modifier
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.LightGray, shape = RoundedCornerShape(5.dp))
                ) {
                    Text(
                        text = logTraces,
                        modifier = Modifier
                            .padding(5.dp)
                            .verticalScroll(rememberScrollState(0)),
                    )
                }
            }
        }
    }

}
