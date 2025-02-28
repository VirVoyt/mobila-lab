package com.example.lab1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.lab1.ui.theme.Lab1Theme
import android.widget.Button
import android.widget.TextView


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main) // Устанавливаем макет
        // Получаем доступ к TextView и Button
        val textView: TextView = findViewById(R.id.textView)
        val button: Button = findViewById(R.id.button)
        // Устанавливаем обработчик нажатия на кнопку
        button.setOnClickListener {
            textView.text = "Кнопка нажата!"
        }
        val a = 10
        val b = 4
        val sum : Int = a + b
        println(sum)
        val person = Person("Иван", 30)
        person.introduce()
    }
}


class Person(val name: String, val age: Int) {

    fun introduce() {
        println("Меня зовут $name, мне $age лет.")
    }
}

