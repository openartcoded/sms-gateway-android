package tech.artcoded.smsgateway

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.telephony.SmsManager
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
                )
            )

            SmsGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    SmsGatewayMainPage("", multiplePermissionsState)
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
        var endpoint by remember {
            mutableStateOf(defaultEndpoint)
        }
        var apiKey by remember { mutableStateOf("") }
        var started by remember { mutableStateOf(false) }
        var logTraces by remember { mutableStateOf("Not started...") }
        val coroutineScope = rememberCoroutineScope()
        val startStopToggle: () -> Unit = {
            coroutineScope.launch {
                started = !started
                logTraces += if (started) "\nStarted..." else {
                    "\nStopped..."
                }
                while (started) {
                    logTraces += "\n sending message..."
                    smsManager.sendTextMessage("+32XXXXXXX", null, "hello", null, null)
                    delay(6000)
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !started,
                        label = { Text(text = "Endpoint") },
                        onValueChange = { newText ->
                            endpoint = newText
                        })
                    Spacer(modifier = Modifier.height(5.dp))
                    TextField(value = apiKey,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !started,
                        label = { Text(text = "Api Key") },
                        onValueChange = { newText ->
                            apiKey = newText
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
