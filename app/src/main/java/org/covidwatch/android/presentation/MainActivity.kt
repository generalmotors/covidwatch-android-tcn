package org.covidwatch.android.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.covidwatch.android.R
import kotlinx.android.synthetic.main.fragment_settings.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
