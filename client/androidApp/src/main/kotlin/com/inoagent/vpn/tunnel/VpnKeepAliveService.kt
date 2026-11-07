package com.inoagent.vpn.tunnel

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.inoagent.vpn.InoagentApplication
import com.inoagent.vpn.MainActivity
import com.inoagent.vpn.R
import kotlinx.coroutines.*

class VpnKeepAliveService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var started=false
    override fun onCreate() {
        super.onCreate()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("vpn","VPN-соединение",NotificationManager.IMPORTANCE_LOW))
        scope.launch { (application as InoagentApplication).container.tunnel.connected.collect { connected->if(started) manager.notify(21,notification(connected)) } }
    }
    private fun notification(connected:Boolean):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,VpnKeepAliveService::class.java).setAction("disconnect"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,"vpn").setSmallIcon(R.drawable.ic_vpn_status).setContentTitle("Inoagent VPN").setContentText(if(connected) "Ваше VPN-соединение активно" else "Подготавливаем VPN-соединение").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Отключить",stop).build()).build()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        started=true
        startForeground(21,notification((application as InoagentApplication).container.tunnel.connected.value))
        if(intent?.action=="disconnect") scope.launch { runCatching { (application as InoagentApplication).container.tunnel.disconnect() };stopSelf() }
        return START_NOT_STICKY
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
