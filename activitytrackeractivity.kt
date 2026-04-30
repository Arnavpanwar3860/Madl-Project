package com.example.smartfit

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import kotlin.random.Random

class ActivityTrackerActivity : AppCompatActivity(), SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    
    // Video Search Views
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerDifficulty: Spinner
    private lateinit var spinnerDuration: Spinner
    private lateinit var btnSearchWorkouts: Button
    private lateinit var tvSearchingStatus: TextView

    // Sensing Views
    private lateinit var statusHeader: TextView
    private lateinit var sensorData: TextView
    private lateinit var sensingCard: MaterialCardView
    private lateinit var reportCard: MaterialCardView
    private lateinit var selectionCard: MaterialCardView
    private lateinit var startBtn: Button
    private lateinit var userName: String
    
    // Report Views
    private lateinit var reportHeartRate: TextView
    private lateinit var reportCalories: TextView
    private lateinit var reportAiInsight: TextView

    // Sensor Variables
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var movementSum = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker)

        userName = intent.getStringExtra("USER_NAME") ?: "Champion"

        // Initialize Video Search
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerDifficulty = findViewById(R.id.spinnerDifficulty)
        spinnerDuration = findViewById(R.id.spinnerDuration)
        btnSearchWorkouts = findViewById(R.id.btnSearchWorkouts)
        tvSearchingStatus = findViewById(R.id.tvSearchingStatus)

        btnSearchWorkouts.setOnClickListener {
            performWorkoutSearch()
        }

        // Initialize AI Sensing
        val running = findViewById<RadioButton>(R.id.running)
        val gym = findViewById<RadioButton>(R.id.gym)
        val yoga = findViewById<RadioButton>(R.id.yoga)
        
        startBtn = findViewById(R.id.startBtn)
        selectionCard = findViewById(R.id.selectionCard)
        sensingCard = findViewById(R.id.sensingCard)
        reportCard = findViewById(R.id.reportCard)
        
        statusHeader = findViewById(R.id.tvStatusHeader)
        sensorData = findViewById(R.id.tvSensorData)
        
        reportHeartRate = findViewById(R.id.reportHeartRate)
        reportCalories = findViewById(R.id.reportCalories)
        reportAiInsight = findViewById(R.id.reportAiInsight)
        val btnDone = findViewById<Button>(R.id.btnDone)

        // Initialize Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val userPrefsKey = "SmartFitPrefs_${userName.lowercase()}"
        val sharedPref = getSharedPreferences(userPrefsKey, Context.MODE_PRIVATE)

        startBtn.setOnClickListener {
            val workout = when {
                running.isChecked -> "Running"
                gym.isChecked -> "Gym"
                yoga.isChecked -> "Yoga"
                else -> "None"
            }

            selectionCard.visibility = View.GONE
            startBtn.visibility = View.GONE
            sensingCard.visibility = View.VISIBLE
            
            movementSum = 0f
            registerSensor()
            simulateSensing(workout, sharedPref)
        }

        btnDone.setOnClickListener {
            finish()
        }
    }

    private fun performWorkoutSearch() {
        val category = spinnerCategory.selectedItem.toString()
        val difficulty = spinnerDifficulty.selectedItem.toString()
        val duration = spinnerDuration.selectedItem.toString()

        tvSearchingStatus.visibility = View.VISIBLE
        
        val query = "$category workout $difficulty level $duration"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
        
        handler.postDelayed({
            tvSearchingStatus.visibility = View.GONE
            startActivity(intent)
        }, 1000)
    }

    private fun registerSensor() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterSensor() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val delta = Math.sqrt(((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ)).toDouble()).toFloat()
            if (delta > 0.5f) {
                movementSum += delta
            }
            
            lastX = x
            lastY = y
            lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun simulateSensing(workout: String, prefs: android.content.SharedPreferences) {
        var elapsed = 0
        var totalBPM = 0
        val iterations = 5
        
        val runnable = object : Runnable {
            override fun run() {
                if (elapsed < iterations * 1000) { 
                    val baseBPM = when(workout) {
                        "Running" -> Random.nextInt(130, 150)
                        "Gym" -> Random.nextInt(110, 130)
                        "Yoga" -> Random.nextInt(75, 95)
                        else -> Random.nextInt(70, 100)
                    }
                    val movementBonus = (movementSum / 10).toInt().coerceAtMost(20)
                    val bpm = baseBPM + movementBonus
                    totalBPM += bpm
                    statusHeader.text = "AI Sensing: $workout..."
                    sensorData.text = "BPM: $bpm | Motion Activity Detected"
                    elapsed += 1000
                    handler.postDelayed(this, 1000)
                } else {
                    unregisterSensor()
                    sensingCard.visibility = View.GONE
                    reportCard.visibility = View.VISIBLE
                    
                    val avgBPM = totalBPM / iterations
                    val calories = when(workout) {
                        "Running" -> Random.nextInt(100, 120)
                        "Gym" -> Random.nextInt(70, 90)
                        "Yoga" -> Random.nextInt(25, 40)
                        else -> 50
                    } + (movementSum / 5).toInt().coerceAtMost(30)
                    
                    reportHeartRate.text = "$avgBPM BPM"
                    reportCalories.text = "$calories kcal"
                    reportAiInsight.text = "Great $workout session! Your movement was consistent."

                    val encodedWorkout = Base64.encodeToString(workout.toByteArray(), Base64.DEFAULT)
                    prefs.edit()
                        .putString("lastWorkout", encodedWorkout)
                        .putString("last_hr", "$avgBPM BPM")
                        .putInt("total_calories", prefs.getInt("total_calories", 0) + calories)
                        .apply()
                }
            }
        }
        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSensor()
        handler.removeCallbacksAndMessages(null)
    }
}
