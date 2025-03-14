package com.example.lab1

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.graphics.Color
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView = findViewById<TextView>(R.id.textView)
        textView.setCustomText("Новый текст", Color.RED)


        Log.d("MainActivity", "onCreate called")//логирование вызова

        val user1 = User("Иван", "ivan@example.com")
        val user2 = User(null, null)

        printUserInfo(user1)  // Вывод: Имя: Иван, Email: ivan@example.com
        printUserInfo(user2)

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DetailFragment())
                .commit()
        }

    }

   private fun TextView.setCustomText(text: String, color: Int) {
        this.text = text
        this.setTextColor(color)
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause called")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy called")
    }
}

class User(val name: String?, val email: String?)

fun printUserInfo(user: User?) {
    // безопасный метод и elvis оператор
    val userName = user?.name ?: "Unknown"
    val userEmail = user?.email ?: "No email"

    println("Имя: $userName, Email: $userEmail")
}