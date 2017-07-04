package io.neoterm.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.support.v4.content.WakefulBroadcastReceiver
import android.util.Log
import io.neoterm.R
import io.neoterm.backend.EmulatorDebug
import io.neoterm.backend.TerminalSession
import io.neoterm.ui.NeoTermActivity
import io.neoterm.utils.TerminalUtils
import java.util.*

/**
 * @author kiva
 */

class NeoTermService : Service() {
    inner class NeoTermBinder : Binder() {
        var service = this@NeoTermService
    }

    private val neoTermBinder = NeoTermBinder()
    private val mTerminalSessions = ArrayList<TerminalSession>()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onBind(intent: Intent): IBinder? {
        return neoTermBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action
        if (ACTION_SERVICE_STOP == action) {
            for (i in mTerminalSessions.indices)
                mTerminalSessions[i].finishIfRunning()
            stopSelf()
        } else if (action != null) {
            Log.e(EmulatorDebug.LOG_TAG, "Unknown NeoTermService action: '$action'")
        }

        if (flags and Service.START_FLAG_REDELIVERY == 0) {
            // Service is started by WBR, not restarted by system, so release the WakeLock from WBR.
            WakefulBroadcastReceiver.completeWakefulIntent(intent)
        }

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(true)

        for (i in mTerminalSessions.indices)
            mTerminalSessions[i].finishIfRunning()
        mTerminalSessions.clear()
    }

    val sessions: List<TerminalSession>
        get() = mTerminalSessions

    fun createTermSession(executablePath: String?, arguments: Array<String>?, cwd: String?, env: Array<String>?, sessionCallback: TerminalSession.SessionChangedCallback?, systemShell: Boolean): TerminalSession {
        val session = TerminalUtils.createSession(this, executablePath, arguments, cwd, env, sessionCallback, systemShell)
        mTerminalSessions.add(session)
        updateNotification()
        return session
    }

    fun removeTermSession(sessionToRemove: TerminalSession): Int {
        val indexOfRemoved = mTerminalSessions.indexOf(sessionToRemove)
        mTerminalSessions.removeAt(indexOfRemoved)
        updateNotification()
        return indexOfRemoved
    }

    private fun updateNotification() {
        if (sessions.isNotEmpty()) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun createNotification(): Notification {
        val notifyIntent = Intent(this, NeoTermActivity::class.java)
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0)

        val sessionCount = mTerminalSessions.size
        val contentText = sessionCount.toString() + " session" + if (sessionCount == 1) "" else "s"

        val builder = Notification.Builder(this)
        builder.setContentTitle(getText(R.string.app_name))
        builder.setContentText(contentText)
        builder.setSmallIcon(R.drawable.ic_terminal_running)
        builder.setContentIntent(pendingIntent)
        builder.setOngoing(true)
        builder.setShowWhen(false)
        builder.setColor(0xFF000000.toInt())

        val exitIntent = Intent(this, NeoTermService::class.java).setAction(ACTION_SERVICE_STOP)
        builder.addAction(android.R.drawable.ic_delete, "Exit", PendingIntent.getService(this, 0, exitIntent, 0))

        return builder.build()
    }

    companion object {
        val ACTION_SERVICE_STOP = "neoterm.action.service.stop"
        private val NOTIFICATION_ID = 52019
    }
}