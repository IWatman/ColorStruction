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
}
