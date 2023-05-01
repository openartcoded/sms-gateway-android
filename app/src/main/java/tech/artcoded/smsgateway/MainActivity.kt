package tech.artcoded.smsgateway

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import info.mqtt.android.service.MqttAndroidClient
import info.mqtt.android.service.QoS
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject
import org.json.JSONTokener
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
                )
            )

            SmsGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    SmsGatewayMainPage(multiplePermissionsState)
                }
            }


        }


    }
}

private fun getSecretSharedPref(context: Context): SharedPreferences {

    val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setUserAuthenticationRequired(true, 10).setRequestStrongBoxBacked(true).build()

    return EncryptedSharedPreferences.create(
        context,
        "sms_gateway_secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

fun startMqtt(
    androidCtx: Context,
    endpoint: String,
    username: String,
    password: String,
    onReceiveNotify: (n: String) -> Unit,
    onFailure: (n: String) -> Unit,
    onReconnect: () -> Unit,
): MqttAndroidClient {
    val mqttAndroidClient = MqttAndroidClient(
        androidCtx, endpoint, "$username-android-client-${System.currentTimeMillis()}"
    )
    val smsManager = androidCtx.getSystemService(SmsManager::class.java)

    mqttAndroidClient.setCallback(object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String) {
            if (reconnect) {
                Toast.makeText(androidCtx, "Reconnected", Toast.LENGTH_SHORT).show()
                // Because Clean Session is true, we need to re-subscribe
                subscribeToTopic(androidCtx, mqttAndroidClient)
                onReconnect()
            } else {
                Toast.makeText(androidCtx, "Connected", Toast.LENGTH_SHORT).show()
            }
        }

        override fun connectionLost(cause: Throwable?) {
            val msg = "The Connection was lost."
            Toast.makeText(androidCtx, msg, Toast.LENGTH_SHORT).show()
            onFailure(msg)
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            val json = JSONTokener(message.toString()).nextValue() as JSONObject
            val phoneNumber = json.getString("phoneNumber")
            val textMessage = json.getString("message")
            smsManager.sendTextMessage(phoneNumber, null, textMessage, null, null)
            onReceiveNotify(phoneNumber)
            Toast.makeText(
                androidCtx, "Incoming message:  $textMessage", Toast.LENGTH_SHORT
            ).show()
        }

        override fun deliveryComplete(token: IMqttDeliveryToken) {}
    })
    val mqttConnectOptions = MqttConnectOptions()
    mqttConnectOptions.isAutomaticReconnect = true
    mqttConnectOptions.isCleanSession = false
    mqttConnectOptions.userName = username
    mqttConnectOptions.password = password.toCharArray()
    mqttAndroidClient.connect(mqttConnectOptions, null, object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken) {
            val disconnectedBufferOptions = DisconnectedBufferOptions()
            disconnectedBufferOptions.isBufferEnabled = true
            disconnectedBufferOptions.bufferSize = 100
            disconnectedBufferOptions.isPersistBuffer = false
            disconnectedBufferOptions.isDeleteOldestMessages = false
            mqttAndroidClient.setBufferOpts(disconnectedBufferOptions)
            subscribeToTopic(androidCtx, mqttAndroidClient)
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.e(this.javaClass.name, "Error: ", exception)
            val msg = "Failed to connect: ${exception?.message}"
            Toast.makeText(
                androidCtx, msg, Toast.LENGTH_SHORT
            ).show()
            onFailure(msg)
        }
    })
    return mqttAndroidClient
}

fun subscribeToTopic(context: Context, mqttAndroidClient: MqttAndroidClient) {
    mqttAndroidClient.subscribe(TOPIC, QoS.AtMostOnce.value, null, object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken) {
            Toast.makeText(context, "Subscribed to $TOPIC", Toast.LENGTH_SHORT).show()
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.e(this.javaClass.name, "Failed to subscribe $exception")
            Toast.makeText(context, "Failed to subscribe to $TOPIC", Toast.LENGTH_SHORT).show()

        }
    })
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SmsGatewayMainPage(
    //defaultEndpoint: String,
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
        var prefs = getSecretSharedPref(androidCtx)
        var endpoint by remember {
            mutableStateOf(prefs.getString("defaultEndpoint", "tcp://192.168.1.133:61616")!!)
        }
        var mqttClient by remember {
            mutableStateOf(null as MqttAndroidClient?)
        }
        var username by remember { mutableStateOf(prefs.getString("username", "")!!) }
        var password by remember { mutableStateOf(prefs.getString("password", "")!!) }
        var passwordVisible by remember { mutableStateOf(false) }
        var started by remember { mutableStateOf(false) }
        var logTraces by remember { mutableStateOf("Not started...") }
        val coroutineScope = rememberCoroutineScope()
        val startStopToggle: () -> Unit = {
            coroutineScope.launch {
                started = !started
                logTraces += if (started) "\nStarted..." else {
                    "\nStopped..."
                }
                mqttClient = if (!started && mqttClient != null) {
                    mqttClient!!.apply { disconnect() }
                    null
                } else {
                    with(prefs.edit()) {
                        putString("username", username)
                        putString("password", password)
                        putString("endpoint", endpoint)
                        commit()
                    }
                    startMqtt(
                        androidCtx,
                        endpoint,
                        username,
                        password,
                        onReceiveNotify = { logTraces += "\nSend message to $it" },
                        onFailure = {
                            logTraces += "\nMQTT Error: $it"
                            started = false
                        },
                        onReconnect = {
                            logTraces += "\nReconnected"
                            started = true
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth()
        ) {
            Scaffold {
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

}
