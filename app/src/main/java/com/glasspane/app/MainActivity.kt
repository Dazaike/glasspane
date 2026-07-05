package com.glasspane.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.glasspane.app.permissions.PermissionsGate
import com.glasspane.app.ui.CameraScreen
import com.glasspane.app.ui.theme.GlasspaneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tied to window visibility/foreground state: stops applying automatically
        // when the app backgrounds, no manual lifecycle teardown needed.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GlasspaneTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionsGate {
                        CameraScreen()
                    }
                }
            }
        }
    }
}
