package com.example.dose.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.dose.ui.iconResource
import androidx.core.app.NotificationCompat
import com.example.dose.MainActivity
import com.example.dose.R
import com.example.dose.data.*
import java.time.LocalDate

/** Only runs while the opt-in pop-up is visible; notifications remain after it times out. */
class ReminderOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val slots = linkedMapOf<String, ReminderSlot>()
    private var preview = false
    private var window: View? = null
    private var renderedKeys: List<String> = emptyList()
    private val manager get() = getSystemService(WINDOW_SERVICE) as WindowManager
    private val expire = Runnable { finishAlert() }
    private val reconcile = object : Runnable {
        override fun run() {
            if (!ReminderAlerts.permissionGranted(this@ReminderOverlayService) || (!preview && !ReminderAlerts.enabled(this@ReminderOverlayService))) {
                finishAlert(); return
            }
            val medications = DoseStore.medications(this@ReminderOverlayService)
            val logs = DoseStore.logs(this@ReminderOverlayService)
            val snoozes = DoseStore.snoozes(this@ReminderOverlayService)
            slots.entries.removeAll { (_, slot) ->
                val date = LocalDate.parse(slot.date)
                date != LocalDate.now() || medications.none { it.id == slot.medicationId && it.active && slot.time in it.timesOn(date) } ||
                    logs.any { it.date == slot.date && it.medicationId == slot.medicationId && it.time == slot.time } ||
                    (snoozes[slot.key] ?: 0L) > System.currentTimeMillis()
            }
            if (!preview && slots.isEmpty()) { finishAlert(); return }
            if (window == null || renderedKeys != slots.keys.toList()) render()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always enter the foreground promptly, even if permission was revoked after dispatch.
        if (!enterForeground()) return START_NOT_STICKY
        val isPreview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true
        val slot = intent?.let(ReminderSlot::from)
        if (slot != null) { preview = false; slots[slot.key] = slot }
        else if (isPreview && slots.isEmpty()) preview = true
        if (!ReminderAlerts.permissionGranted(this) || (!preview && !ReminderAlerts.enabled(this)) || (!preview && slots.isEmpty())) {
            finishAlert(); return START_NOT_STICKY
        }
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, 120_000)
        handler.removeCallbacks(reconcile)
        reconcile.run()
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean {
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Reminder pop-up", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Medication pop-up is open").setContentText("Take, snooze, skip, or close the pop-up.")
            .setContentIntent(open).setOngoing(true).setSilent(true).build()
        return try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(SERVICE_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            else startForeground(SERVICE_NOTIFICATION, notification)
            true
        } catch (_: SecurityException) {
            finishAlert(); false
        } catch (_: IllegalStateException) {
            finishAlert(); false
        }
    }

    private fun render() {
        val themed = ContextThemeWrapper(this, R.style.Theme_Dose)
        val content = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(16))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(22).toFloat() }
            elevation = dp(12).toFloat()
        }
        fun text(value: String, size: Float = 16f, bold: Boolean = false) = TextView(themed).apply {
            text = value; textSize = size; setTextColor(Color.rgb(37, 43, 67))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }
        content.addView(text(if (preview) "Test reminder" else "Time for your medication", 21f, true))
        if (preview) {
            content.addView(text("This is how Dose appears over other apps. No dose will be recorded."))
        } else {
            val medications = DoseStore.medications(this).associateBy { it.id }
            slots.values.forEach { slot ->
                val medication = medications[slot.medicationId] ?: return@forEach
                val heading = LinearLayout(themed).apply { gravity = Gravity.CENTER_VERTICAL }
                heading.addView(ImageView(themed).apply {
                    setImageResource(medication.form.iconResource()); setColorFilter(Color.parseColor(MedicationColors.hex(medication.color)))
                    contentDescription = medication.form.label
                }, LinearLayout.LayoutParams(dp(34), dp(34)))
                heading.addView(text(medication.name, 19f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
                content.addView(heading)
                content.addView(text("${medication.dose} · Scheduled ${slot.time}"))
                if (medication.note.isNotBlank()) content.addView(text(medication.note, 14f))
                listOf("Taken" to ReminderReceiver.ACTION_TAKEN, "Snooze 10 min" to ReminderReceiver.ACTION_SNOOZE, "Skip" to ReminderReceiver.ACTION_SKIP).forEach { (label, action) ->
                    content.addView(Button(themed).apply {
                        this.text = label; isAllCaps = false
                        setOnClickListener {
                            // Reuse notification actions, including their duplicate-log and schedule validation.
                            ReminderScheduler.actionIntent(this@ReminderOverlayService, action, slot.medicationId, slot.time, slot.date, slot.scheduledAt).send()
                            slots.remove(slot.key)
                            if (slots.isEmpty()) finishAlert() else render()
                        }
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
                }
            }
        }
        content.addView(Button(themed).apply {
            text = if (preview) "Close test" else "Later · keep notification"; isAllCaps = false
            setOnClickListener { finishAlert() }
        })
        val scroll = ScrollView(themed).apply { isFillViewport = true; addView(content) }
        val metrics = resources.displayMetrics
        val width = minOf(metrics.widthPixels - dp(32), dp(420)).coerceAtLeast(1)
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val height = minOf(content.measuredHeight, (metrics.heightPixels * 0.8).toInt())
        val params = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER; setTitle("Dose medication reminder") }
        try {
            window?.let { manager.removeView(it) }
            window = null
            manager.addView(scroll, params)
            window = scroll
            renderedKeys = slots.keys.toList()
        } catch (_: RuntimeException) {
            // Permission revocation / protected system surfaces must leave the ordinary reminder intact.
            finishAlert()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (window != null) render()
    }
    override fun onTimeout(startId: Int) { finishAlert() }
    override fun onTimeout(startId: Int, fgsType: Int) { finishAlert() }
    private fun finishAlert() {
        handler.removeCallbacksAndMessages(null)
        window?.let { runCatching { manager.removeView(it) } }
        window = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { finishAlert(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        const val EXTRA_PREVIEW = "preview"
        private const val CHANNEL = "dose_popup_service"
        private const val SERVICE_NOTIFICATION = -1042
    }
}
