package tech.artcoded.smsgateway

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tech.artcoded.smsgateway.ui.theme.SmsGatewayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Sms Gateway"
        setContent {
            SmsGatewayTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")

                }
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(endpoint: String, modifier: Modifier = Modifier) {
    var endpoint by remember {
        mutableStateOf(endpoint)
    }
    var apiKey by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    var logTraces by remember { mutableStateOf("Not started...") }
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
                TextField(value = endpoint, modifier = Modifier.fillMaxWidth(), enabled = !started,

                    label = { Text(text = "Endpoint") }, onValueChange = { newText ->
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
                    onClick = {
                        started = !started
                        logTraces += if (started) "\nStarted..." else {
                            "\nStopped..."
                        }
                    }, modifier = Modifier.fillMaxWidth(),
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {

    SmsGatewayTheme {
        Greeting(endpoint = "http://google.com")
    }
}
