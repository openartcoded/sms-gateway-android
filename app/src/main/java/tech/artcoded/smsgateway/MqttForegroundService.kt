package tech.artcoded.smsgateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
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

class MqttForegroundService() : Service() {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var mqttAndroidClient: MqttAndroidClient? = null
    private lateinit var notificationManager: NotificationManager
    private var isStarted = false
    override fun onCreate() {
        super.onCreate()
        // initialize dependencies here (e.g. perform dependency injection)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    companion object {
        private const val ONGOING_NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "1001"
        val BUS = MutableLiveData<Boolean>()
        val onFailure: (context: Context) -> Unit = {
            if (BUS.hasActiveObservers()) {
                BUS.postValue(false)
            }
        }
        val onSubscribed: (context: Context) -> Unit = {
            if (BUS.hasActiveObservers()) {
                BUS.postValue(true)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(!isStarted) {
            makeForeground()
            if (mqttAndroidClient?.isConnected != true) {
                val prefs = Utils.createEncryptedSharedPrefDestructively(context = baseContext)
                mqttAndroidClient = startMqtt(
                    baseContext,
                    coroutineScope,
                    prefs.getString("endpoint", "")!!,
                    prefs.getString("username", "")!!,
                    prefs.getString("password", "")!!,
                    onReceiveNotify = {},
                    onFailure = onFailure,
                    onSubscribed = onSubscribed
                )
            } else {
                onSubscribed(baseContext)
            }
            isStarted = true
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException()
    }

    override fun onDestroy() {
        super.onDestroy()
        onFailure(this)
        mqttAndroidClient?.disconnect()
        isStarted = false
    }
    private fun makeForeground() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        createServiceNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Artcoded SMS Service")
            .setContentText("Artcoded SMS Service is running")
            .setSmallIcon(androidx.core.R.drawable.ic_call_answer)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }
    private fun createServiceNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        notificationManager.createNotificationChannel(channel)
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
            try {
                if (reconnect) {
                    subscribeToTopic(androidCtx, mqttAndroidClient, onSubscribed)
                    Toast.makeText(androidCtx, "Reconnected", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(androidCtx, "Connected", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(this.javaClass.name, " connect complete error: $e")
            }
        }

        override fun connectionLost(cause: Throwable?) {
            try {
                val msg = "The Connection was lost."
                onFailure(androidCtx)
                Toast.makeText(androidCtx, msg, Toast.LENGTH_SHORT).show()
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
    mqttConnectOptions.keepAliveInterval = 30
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
            try {
                onFailure(androidCtx)
                val msg = "Failed to connect: ${exception?.message}"
                Toast.makeText(
                    androidCtx, msg, Toast.LENGTH_SHORT
                ).show()
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
            try {
                onSubscribed(context)
                Toast.makeText(context, "Subscribed to $TOPIC", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(this.javaClass.name, "could not call onSuccess ${e.message}")
            }
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.e(this.javaClass.name, "Failed to subscribe $exception")
            try {
                Toast.makeText(context, "Failed to subscribe to $TOPIC", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(this.javaClass.name, "$e")
            }
        }
    })
}
