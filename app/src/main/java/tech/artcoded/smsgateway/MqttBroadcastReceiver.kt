package tech.artcoded.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MqttBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            val serviceIntent = Intent(
                context,
                MqttForegroundService::class.java
            )
            serviceIntent.action = START_MQTT_SERVICE_ACTION
            context.startForegroundService(serviceIntent)
        }
    }
}