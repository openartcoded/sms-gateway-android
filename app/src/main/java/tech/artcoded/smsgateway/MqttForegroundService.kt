package tech.artcoded.smsgateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import info.mqtt.android.service.MqttAndroidClient
import info.mqtt.android.service.QoS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject
import org.json.JSONTokener


const val START_MQTT_SERVICE_ACTION = "START_MQTT_SERVICE_ACTION"
const val STOP_MQTT_SERVICE_ACTION = "STOP_MQTT_SERVICE_ACTION"
const val CHECK_STARTED_MQTT_SERVICE_ACTION = "CHECK_STARTED_MQTT_SERVICE_ACTION"

class MqttForegroundService() : Service() {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var mqttAndroidClient: MqttAndroidClient? = null

    companion object {
        val BUS = MutableLiveData<Boolean>()
        val onFailure: (context: Context) -> Unit = {
            if (BUS.hasActiveObservers()) {
                BUS.postValue(false)
            }/*   with(Utils.createEncryptedSharedPrefDestructively(context = it).edit()) {
                   putBoolean("isConnected", false)
                   commit()
               }*/
        }
        val onSubscribed: (context: Context) -> Unit = {
            if (BUS.hasActiveObservers()) {
                BUS.postValue(true)
            }/* with(Utils.createEncryptedSharedPrefDestructively(it).edit()) {
                 putBoolean("isConnected", true)
                 commit()
             }*/
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: CHECK_STARTED_MQTT_SERVICE_ACTION) {
            START_MQTT_SERVICE_ACTION, CHECK_STARTED_MQTT_SERVICE_ACTION -> {
                if (this.mqttAndroidClient?.isConnected != true) {
                    val prefs = Utils.createEncryptedSharedPrefDestructively(context = baseContext)
                    this.mqttAndroidClient = startMqtt(
                        baseContext,
                        coroutineScope,
                        prefs.getString("endpoint", "")!!,
                        prefs.getString("username", "")!!,
                        prefs.getString("password", "")!!,
                        onReceiveNotify = {},
                        onFailure = onFailure,
                        onSubscribed = onSubscribed
                    )
                }


            }

            STOP_MQTT_SERVICE_ACTION -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }

        }

        val channelId = "Artcoded SMS Service ID"
        val channel = NotificationChannel(
            channelId, channelId, NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(
            NotificationManager::class.java
        ).createNotificationChannel(channel)
        val notification: Notification.Builder =
            Notification.Builder(this, channelId).setContentText("Artcoded SMS Service is running")
                .setContentTitle("Service enabled")

        startForeground(1001, notification.build())

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        onFailure(this)
        mqttAndroidClient?.disconnect()
        super.onDestroy()
    }
}

private fun startMqtt(
    androidCtx: Context,
    coroutineScope: CoroutineScope,
    endpoint: String,
    username: String,
    password: String,
    onReceiveNotify: (n: String) -> Unit,
    onFailure: (context: Context) -> Unit,
    onSubscribed: (context: Context) -> Unit,
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
                subscribeToTopic(androidCtx, mqttAndroidClient, onSubscribed)

            } else {
                Toast.makeText(androidCtx, "Connected", Toast.LENGTH_SHORT).show()
            }
        }

        override fun connectionLost(cause: Throwable?) {
            val msg = "The Connection was lost."
            Toast.makeText(androidCtx, msg, Toast.LENGTH_SHORT).show()
            try {
                onFailure(androidCtx)
            } catch (e: Exception) {
                Log.e(this.javaClass.name, "could not call onFailure ${e.message}")
            }
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            coroutineScope.launch {
                val json = JSONTokener(message.toString()).nextValue() as JSONObject
                val phoneNumber = json.getString("phoneNumber")
                val textMessages = smsManager.divideMessage(json.getString("message"))
                for (textMessage in textMessages) {
                    smsManager.sendTextMessage(phoneNumber, null, textMessage, null, null)

                    try {
                        onReceiveNotify(phoneNumber)
                    } catch (e: Exception) {
                        Log.e(this.javaClass.name, "could not call onReceiveNotify ${e.message}")
                    }
                    Log.d(this.javaClass.name, "Incoming message:  $textMessage")
                    delay(5000)
                }
            }
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
            subscribeToTopic(androidCtx, mqttAndroidClient, onSubscribed)
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.e(this.javaClass.name, "Error: ", exception)
            val msg = "Failed to connect: ${exception?.message}"
            Toast.makeText(
                androidCtx, msg, Toast.LENGTH_SHORT
            ).show()

            try {
                onFailure(androidCtx)
            } catch (e: Exception) {
                Log.e(this.javaClass.name, "could not call onFailure ${e.message}")
            }
        }
    })
    return mqttAndroidClient
}

private fun subscribeToTopic(
    context: Context, mqttAndroidClient: MqttAndroidClient, onSubscribed: (context: Context) -> Unit
) {
    mqttAndroidClient.subscribe(TOPIC, QoS.AtMostOnce.value, null, object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken) {
            Toast.makeText(context, "Subscribed to $TOPIC", Toast.LENGTH_SHORT).show()
            try {
                onSubscribed(context)
            } catch (e: Exception) {
                Log.e(this.javaClass.name, "could not call onSuccess ${e.message}")
            }
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.e(this.javaClass.name, "Failed to subscribe $exception")
            Toast.makeText(context, "Failed to subscribe to $TOPIC", Toast.LENGTH_SHORT).show()

        }
    })
}
