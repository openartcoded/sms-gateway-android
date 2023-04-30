package tech.artcoded.smsgateway

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import tech.artcoded.smsgateway.ui.theme.SmsGatewayTheme

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
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
                    SmsGatewayMainPage("tcp://192.168.1.133:61616", multiplePermissionsState)
                }
            }


        }


    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SmsGatewayMainPage(
    defaultEndpoint: String,
    multiplePermissionsState: MultiplePermissionsState,
    modifier: Modifier = Modifier
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
        val smsManager = androidCtx.getSystemService(SmsManager::class.java)
        val topic = "sms"
        var endpoint by remember {
            mutableStateOf(defaultEndpoint)
        }
        var mqttClient by remember {
            mutableStateOf(null as MQTTClient?)
        }
        var username by remember { mutableStateOf("artemis") }
        var password by remember { mutableStateOf("artemis") }
        var started by remember { mutableStateOf(false) }
        var logTraces by remember { mutableStateOf("Not started...") }
        val coroutineScope = rememberCoroutineScope()
        val startStopToggle: () -> Unit = {
            coroutineScope.launch {
                started = !started
                logTraces += if (started) "\nStarted..." else {
                    "\nStopped..."
                }
                if (!started && mqttClient != null) {
                    mqttClient!!.unsubscribe(topic, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(this.javaClass.name, "client unsubscribed")
                            mqttClient!!.disconnect(object : IMqttActionListener {
                                override fun onSuccess(asyncActionToken: IMqttToken?) {
                                    Log.d(this.javaClass.name, "client disconnected")
                                    mqttClient!!.close()
                                }

                                override fun onFailure(
                                    asyncActionToken: IMqttToken?,
                                    exception: Throwable?
                                ) {
                                    Log.e(this.javaClass.name, "could not disconnect $exception")
                                }
                            })
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            Log.e(this.javaClass.name, "could not unsubscribe $exception")
                        }
                    })

                } else {
                    mqttClient = MQTTClient(context = androidCtx, endpoint)
                    mqttClient?.connect(username,
                        password,
                        cbConnect = object : IMqttActionListener {
                            override fun onSuccess(asyncActionToken: IMqttToken?) {
                                Log.d(this.javaClass.name, "Connection success")

                                Toast.makeText(
                                    androidCtx,
                                    "MQTT Connection success",
                                    Toast.LENGTH_SHORT
                                ).show()
                                mqttClient?.subscribe(topic, 1,
                                    object : IMqttActionListener {
                                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                                            val msg = "Subscribed to: sms"
                                            Log.d(this.javaClass.name, msg)

                                            Toast.makeText(androidCtx, msg, Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onFailure(
                                            asyncActionToken: IMqttToken?,
                                            exception: Throwable?
                                        ) {
                                            Log.d(this.javaClass.name, "Failed to subscribe: sms $exception")
                                            Toast.makeText(
                                                androidCtx,
                                                "failed to subscribe ${exception.toString()}",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                        }
                                    })
                            }

                            override fun onFailure(
                                asyncActionToken: IMqttToken?,
                                exception: Throwable?
                            ) {
                                Log.d(
                                    this.javaClass.name,
                                    "Connection failure: ${exception.toString()}"
                                )

                                Toast.makeText(
                                    androidCtx,
                                    "MQTT Connection fails: ${exception.toString()}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        cbClient = object : MqttCallback {
                            override fun messageArrived(topic: String?, message: MqttMessage?) {
                                val msg =
                                    "Receive message: ${message.toString()} from topic: $topic"
                                Log.d(this.javaClass.name, msg)

                                Toast.makeText(androidCtx, msg, Toast.LENGTH_SHORT).show()
                            }

                            override fun connectionLost(cause: Throwable?) {
                                Log.d(this.javaClass.name, "Connection lost ${cause.toString()}")
                            }

                            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                                Log.d(this.javaClass.name, "Delivery complete")
                            }
                        })

                }
                /* while (started) {
                     logTraces += "\n sending message..."
                     smsManager.sendTextMessage("+32XXXXXXX", null, "hello", null, null)
                     delay(6000)
                 }*/
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !started,
                        label = { Text(text = "Endpoint") },
                        onValueChange = { newText ->
                            endpoint = newText
                        })
                    Spacer(modifier = Modifier.height(5.dp))
                    TextField(value = username,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !started,
                        label = { Text(text = "Username") },
                        onValueChange = { newText ->
                            username = newText
                        })
                    Spacer(modifier = Modifier.height(5.dp))
                    TextField(value = password,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !started,
                        label = { Text(text = "Password") },
                        onValueChange = { newText ->
                            password = newText
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
