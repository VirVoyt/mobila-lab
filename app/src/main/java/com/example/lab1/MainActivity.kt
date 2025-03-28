package com.example.lab1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import retrofit2.Retrofit//для выполнения сетевых запросов
import retrofit2.converter.gson.GsonConverterFactory//Конвертирует JSON-ответы от сервера в объекты Kotlin/Java и наоборот.
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout//Контейнер для реализации "pull-to-refresh"
import kotlinx.coroutines.Dispatchers//Определяет потоки для корутин
import kotlinx.coroutines.launch//Запускает корутину (асинхронную задачу)
import kotlinx.coroutines.withContext//Переключает контекст корутины
import retrofit2.http.GET//Аннотация для HTTP-запросов в Retrofit. Определяет тип запроса и эндпоинт.
import androidx.lifecycle.lifecycleScope//Скоуп корутин, привязанный к жизненному циклу Activity/Fragment.
import okhttp3.OkHttpClient//для управления сетевыми запросами
import okhttp3.logging.HttpLoggingInterceptor//Логирует HTTP-запросы/ответы для отладки.
import java.util.concurrent.TimeUnit//Задает единицы измерения времени для таймаутов.

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PostAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        adapter = PostAdapter(emptyList())
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadPosts()

        swipeRefresh.setOnRefreshListener {
            loadPosts()
        }
    }

    private fun loadPosts() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val posts = RetrofitClient.apiService.getPosts()
                withContext(Dispatchers.Main) {
                    adapter.updateData(posts)
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }
}

data class Post(
    val id: Int,
    val title: String,
    val body: String
)

interface ApiService {
    @GET("/posts/1/comments")
    suspend fun getPosts(): List<Post>
}

object RetrofitClient {
    private const val BASE_URL = "https://jsonplaceholder.typicode.com"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(ApiService::class.java)
    }
}

class PostAdapter(private var posts: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val body: TextView = itemView.findViewById(R.id.body)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.title.text = posts[position].title
        holder.body.text = posts[position].body
    }

    override fun getItemCount() = posts.size

    fun updateData(newPosts: List<Post>) {
        posts = newPosts
        notifyDataSetChanged()
    }
}