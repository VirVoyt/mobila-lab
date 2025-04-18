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
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
// Для работы с FlexboxLayout
import com.google.android.flexbox.FlexboxLayout

// Для работы с Material Chip
import com.google.android.material.chip.Chip

// Для Glide с анимацией перехода
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

// Для преобразования dp в px (если используется)
import android.content.res.Resources

// Для стилей (если используется)
import androidx.annotation.StyleRes
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.room.TypeConverters  // Add this line

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
    @PrimaryKey val article_id: String,
    val title: String,
    val url: String,
    val source: String,
    val author: String?,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?,
    val keywords: List<String>? // Добавляем поле для ключевых слов
) {
    init {
        require(article_id.isNotBlank()) { "Article ID cannot be blank" }
        require(title.isNotBlank()) { "Title cannot be blank" }
        require(url.isNotBlank()) { "URL cannot be blank" }
    }
}

data class NewsResponse(
    val status: String,
    val totalResults: Int,
    val results: List<ArticleResponse>,
    val nextPage: String?
)

data class ArticleResponse(
    val article_id: String,
    val title: String?,
    val link: String?,
    val keywords: List<String>?,
    val creator: List<String>?,
    val description: String?,
    val content: String?,
    val pubDate: String?,
    val image_url: String?,
    val source_id: String?,
    val source_name: String?,
    val language: String?,
    val country: List<String>?,
    val category: List<String>?
)

@Dao
interface ArticleDao {
    @androidx.room.Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun getAllArticles(): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<Article>)

    @androidx.room.Query("DELETE FROM articles")
    suspend fun deleteAllArticles()
}

@Database(entities = [Article::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)  // Add this line
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

object RetrofitClient {
    private const val BASE_URL = "https://newsdata.io/api/1/"

    val newsApiService: NewsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient())
            .build()
            .create(NewsApiService::class.java)
    }
}

interface NewsApiService {
    @GET("latest?apikey=pub_815148c662de280c4a7d3870ec6429d18e1e5&q=pizza")
    suspend fun getNews(): NewsResponse
}

class NewsRepository(
    private val articleDao: ArticleDao,
    private val newsApiService: NewsApiService,
    private val context: Context
) {
    val articles: Flow<List<Article>> = articleDao.getAllArticles()

    suspend fun refreshArticles() {
        try {
            val response = newsApiService.getNews()

            when {
                response.status != "success" ->
                    throw Exception("API error: ${response.status}")
                response.results.isEmpty() ->
                    throw Exception("No articles found")
                else -> {
                    articleDao.deleteAllArticles()

                    val validArticles = response.results.mapNotNull { apiArticle ->
                        try {
                            Article(
                                article_id = apiArticle.article_id,
                                title = apiArticle.title ?: "No title",
                                url = apiArticle.link ?: "",
                                source = apiArticle.source_name ?: "Unknown source",
                                author = apiArticle.creator?.joinToString(),
                                description = apiArticle.description,
                                urlToImage = apiArticle.image_url,
                                publishedAt = apiArticle.pubDate ?: "",
                                content = apiArticle.content,
                                keywords = apiArticle.keywords // Добавляем ключевые слова
                            )
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }.take(20)

                    if (validArticles.isEmpty()) {
                        throw Exception("No valid articles after filtering")
                    }

                    articleDao.insertArticles(validArticles)
                    sendNotification(context, validArticles.size)
                }
            }
        } catch (e: Exception) {
            throw Exception("Refresh failed: ${e.message}")
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
        val newsApiService = RetrofitClient.newsApiService
        repository = NewsRepository(articleDao, newsApiService, application)
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
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val articleIdTextView: TextView = itemView.findViewById(R.id.articleIdTextView)
        private val keywordsLayout: FlexboxLayout = itemView.findViewById(R.id.keywordsLayout)

        fun bind(article: Article) {
            // Установка текстовых данных
            titleTextView.text = article.title
            descriptionTextView.text = article.description ?: "No description available"
            sourceTextView.text = article.source
            dateTextView.text = article.publishedAt.take(10) // Показываем только дату
            articleIdTextView.text = "ID: ${article.article_id.take(8)}..."

            // Полная реализация загрузки изображения через Glide
            Glide.with(itemView.context)
                .load(article.urlToImage) // URL изображения из статьи
                .transition(DrawableTransitionOptions.withCrossFade(300)) // Анимация перехода 300ms
                .placeholder(R.drawable.ic_image_placeholder) // Заглушка во время загрузки
                .error(R.drawable.ic_broken_image) // Иконка при ошибке загрузки
                .fallback(R.drawable.ic_no_image) // Если URL null или пустой
                .centerCrop() // Масштабирование с заполнением
                .override(800, 600) // Опционально: установка максимальных размеров
                .into(imageView)

            // Очистка и добавление ключевых слов
            keywordsLayout.removeAllViews()

            article.keywords?.take(3)?.forEach { keyword ->
                val chip = Chip(itemView.context).apply {
                    text = keyword
                    setTextAppearance(R.style.ChipTextStyle)
                    isClickable = false
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.chip_horizontal_padding),
                        resources.getDimensionPixelSize(R.dimen.chip_vertical_padding),
                        resources.getDimensionPixelSize(R.dimen.chip_horizontal_padding),
                        resources.getDimensionPixelSize(R.dimen.chip_vertical_padding)
                    )
                }
                keywordsLayout.addView(chip)
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
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}