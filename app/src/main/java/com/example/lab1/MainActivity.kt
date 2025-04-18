package com.example.lab1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.flow.Flow
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.room.OnConflictStrategy
import retrofit2.http.GET
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.lab1.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.lang.Exception
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.LayoutInflater
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: NewsViewModel
    private lateinit var adapter: NewsAdapter
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupRecyclerView()
        setupSwipeRefresh()
        observeData()

        // Загружаем данные при создании активности
        viewModel.refreshArticles()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[NewsViewModel::class.java]
    }

    private fun setupRecyclerView() {
        adapter = NewsAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshArticles()
        }
    }

    private fun observeData() {
        viewModel.articles.observe(this) { articles ->
            adapter.submitList(articles)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading ?: false
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val url: String,
    val source: String,
    val author: String?,
    val title: String,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?
)

data class NewsResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<Article>
)

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun getAllArticles(): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<Article>)

    @Query("DELETE FROM articles")
    suspend fun deleteAllArticles()
}

@Database(entities = [Article::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "news_database"
            ).fallbackToDestructiveMigration()
                .build()
    }
}

interface NewsApiService {
    @GET("v2/top-headlines?country=us&category=business&apiKey=fe2ea13c53374c828bfc007fcb53b61a")
    suspend fun getTopHeadlines(): NewsResponse  // Без параметров

    companion object {
        fun create(): NewsApiService {  // Без параметра apiKey
            return Retrofit.Builder()
                .baseUrl("https://newsapi.org/v2")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NewsApiService::class.java)
        }
    }
}

class NewsRepository(
    private val articleDao: ArticleDao,
    private val context: Context

) {

    private val newsApiService = NewsApiService.create()
    val articles: Flow<List<Article>> = articleDao.getAllArticles()

    suspend fun refreshArticles() {
        try {
            val response = newsApiService.getTopHeadlines()
            articleDao.deleteAllArticles()
            articleDao.insertArticles(response.articles)

            if (response.articles.isNotEmpty()) {
                sendNotification(context, response.articles.size)
            }
        } catch (e: Exception) {
            throw Exception("Failed to refresh articles: ${e.message}")
        }
    }

    private fun sendNotification(context: Context, newArticlesCount: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "news_channel",
                "News Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New articles notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "news_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Articles Available")
            .setContentText("$newArticlesCount new articles loaded")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(1, notification)
    }
}

class NewsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NewsRepository
    val articles: LiveData<List<Article>>
    val isLoading = MutableLiveData<Boolean>()
    val errorMessage = MutableLiveData<String?>()

    init {
        val articleDao = AppDatabase.getDatabase(application).articleDao()
        repository = NewsRepository(
            articleDao,
            application.applicationContext
        )
        articles = repository.articles.asLiveData()
    }

    fun refreshArticles() {
        viewModelScope.launch {
            isLoading.postValue(true)
            try {
                repository.refreshArticles()
                errorMessage.postValue(null)
            } catch (e: Exception) {
                errorMessage.postValue(e.message ?: "Unknown error")
            } finally {
                isLoading.postValue(false)
            }
        }
    }
}

class NewsAdapter : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {
    private var articles = emptyList<Article>()

    inner class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val sourceTextView: TextView = itemView.findViewById(R.id.sourceTextView)

        fun bind(article: Article) {
            titleTextView.text = article.title
            descriptionTextView.text = article.description ?: "No description available"
            sourceTextView.text = article.source

            article.urlToImage?.let { url ->
                Glide.with(itemView)
                    .load(url)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(imageView)
            } ?: run {
                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_article, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount() = articles.size

    fun submitList(newArticles: List<Article>) {
        articles = newArticles
        notifyDataSetChanged()
    }
}