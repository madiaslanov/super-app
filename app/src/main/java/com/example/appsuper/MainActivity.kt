package com.example.appsuper

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnDeviceA).setOnClickListener {
            startActivity(Intent(this, WifiClientActivity::class.java))
        }

        findViewById<Button>(R.id.btnDeviceB).setOnClickListener {
            startActivity(Intent(this, WifiServerActivity::class.java))
        }
    }
}