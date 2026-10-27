package com.colorstruction

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.colorstruction.ui.ColorStructionRoot
import com.colorstruction.ui.theme.ColorStructionTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorStructionTheme {
                ColorStructionRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Unsticks the updater after the install dialog, a dead session or a granted permission.
        (application as ColorStructionApp).container.updateManager.onAppResumed()
    }
}
