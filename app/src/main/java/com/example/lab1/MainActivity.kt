package com.example.lab1

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.DividerItemDecoration
import android.util.SparseBooleanArray
import androidx.annotation.NonNull
import androidx.recyclerview.widget.DiffUtil
import androidx.room.ColumnInfo
import androidx.room.Ignore
import android.graphics.Paint
import androidx.recyclerview.widget.DefaultItemAnimator;
class MainActivity : AppCompatActivity() {
    private lateinit var adapter: TaskAdapter
    private lateinit var taskDao: TaskDao
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddTask: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        fabAddTask = findViewById(R.id.fabAddTask)

        // Инициализация базы данных
        val db = AppDatabase.getDatabase(this)
        taskDao = db.taskDao()

        setupRecyclerView()
        setupAddButton()
        observeTasks()
        recyclerView.itemAnimator = DefaultItemAnimator()
    }

    private fun setupRecyclerView() {
        adapter = TaskAdapter(
            onTaskClick = { task ->
                lifecycleScope.launch {
                    // Обновляем задачу в базе данных
                    taskDao.updateTask(task)
                    // Обновляем список, чтобы применить сортировку
                    taskDao.getAllTasks().collect { updatedTasks ->
                        adapter.submitList(updatedTasks)
                    }
                }
            },
            onTaskLongClick = { task ->
                showDeleteDialog(task)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            itemAnimator = DefaultItemAnimator().apply {
                changeDuration = 120
                moveDuration = 120
            }
            addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupAddButton() {
        fabAddTask.setOnClickListener {
            showAddTaskDialog()
        }
    }

    private fun showAddTaskDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.etTaskTitle)
        val descInput = dialogView.findViewById<EditText>(R.id.etTaskDescription)

        AlertDialog.Builder(this)
            .setTitle("Добавить задачу")
            .setView(dialogView)
            .setPositiveButton("Добавить") { _, _ ->
                val title = titleInput.text.toString()
                val description = descInput.text.toString()

                if (title.isNotBlank()) {
                    lifecycleScope.launch {
                        taskDao.insertTask(Task(title = title, description = description))
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteDialog(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("Удалить задачу?")
            .setMessage("Вы уверены, что хотите удалить '${task.title}'?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    taskDao.deleteTask(task)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun observeTasks() {
        lifecycleScope.launch {
            taskDao.getAllTasks().collect { tasks ->
                adapter.submitList(tasks)
            }
        }
    }

}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,

    var isCompleted: Boolean = false
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, id DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)
}

@Database(entities = [Task::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class TaskAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onTaskLongClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var tasks = emptyList<Task>()

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        private val completedCheckBox: CheckBox = itemView.findViewById(R.id.completedCheckBox)
        private var currentTask: Task? = null

        init {
            // Один раз устанавливаем слушатель в init
            completedCheckBox.setOnCheckedChangeListener { _, isChecked ->
                currentTask?.let { task ->
                    if (task.isCompleted != isChecked) {  // Проверяем реальное изменение
                        task.isCompleted = isChecked

                        onTaskClick(task)
                    }
                }
            }
            itemView.setOnLongClickListener {
                currentTask?.let {
                    onTaskLongClick(it)
                    true  // Возвращаем true, чтобы показать, что событие обработано
                } ?: false
            }
        }

        fun bind(task: Task) {
            currentTask = task

            // Временно отключаем слушатель для обновления состояния
            completedCheckBox.jumpDrawablesToCurrentState()
            completedCheckBox.isChecked = task.isCompleted

            titleTextView.text = task.title
            descriptionTextView.text = task.description
        }


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount() = tasks.size

    fun submitList(newTasks: List<Task>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = tasks.size
            override fun getNewListSize() = newTasks.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                tasks[oldPos].id == newTasks[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                tasks[oldPos] == newTasks[newPos]
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        tasks = newTasks
        diffResult.dispatchUpdatesTo(this)
    }
}

