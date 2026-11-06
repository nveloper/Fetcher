package node.nveloper.fetcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * 삼성 Freecess/MARs의 백그라운드 '얼리기(Frozen)'에 지지 않도록,
 * 프로세스를 상시 살아있게 유지하는 상주 포그라운드 서비스.
 * 접근성 서비스가 연결될 때(부팅 포함)와 앱 실행 시 시작된다.
 */
public class KeepAliveService extends Service {

    private static final String TAG = "Fetcher/Keep";
    private static final String CHANNEL_ID = "fetcher_running";
    private static final int NOTI_ID = 1001;

    /** Doze 화이트리스트/SYSTEM_ALERT_WINDOW 예외가 있으면 백그라운드에서도 시작 가능. */
    public static void start(Context ctx) {
        try {
            Intent i = new Intent(ctx, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Throwable t) {
            Log.w(TAG, "start failed", t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();

        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Fetcher 실행 중")
                .setContentText("카톡 인증번호를 감시하고 있습니다.")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTI_ID, n);
            }
        } catch (Throwable t) {
            Log.e(TAG, "startForeground failed", t);
        }
        return START_STICKY;
    }

    private void ensureChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Fetcher 상태", NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("앱이 꺼지지 않도록 유지하는 상태 알림");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
