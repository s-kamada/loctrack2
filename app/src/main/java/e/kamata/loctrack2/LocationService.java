package e.kamata.loctrack2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

public class LocationService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //開始時に通知とアイコンを表示するメソッド、startForegroundのためにはこれが必要らしい
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(Build.VERSION.SDK_INT>=26) {
            final int ID = 1;
            int requestCode = 0;
            String channelId = "default";
            String title = this.getString(R.string.app_name);

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, requestCode,
                            intent, PendingIntent.FLAG_UPDATE_CURRENT);
            // ForegroundにするためNotificationが必要、Contextを設定
            NotificationManager notificationManager =
                    (NotificationManager) this.
                            getSystemService(Context.NOTIFICATION_SERVICE);
            // Notification　Channel 設定
            NotificationChannel channel = new NotificationChannel(
                    channelId, title, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Silent Notification");
            // 通知音を消さないと毎回通知音が出てしまう
            // この辺りの設定はcleanにしてから変更
            channel.setSound(null, null);
            // 通知ランプを消す
            channel.enableLights(false);
            channel.setLightColor(Color.BLUE);
            // 通知バイブレーション無し
            channel.enableVibration(false);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Notification notification = new Notification.Builder(this, channelId)
                        .setContentTitle(title)
                        // 本来なら衛星のアイコンですがandroid標準アイコンを設定
                        .setSmallIcon(android.R.drawable.btn_star)
                        .setContentText("位置情報を取得しています")
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setWhen(System.currentTimeMillis())
                        .build();

                // startForeground
                startForeground(ID, notification);
            }
        }
        return START_NOT_STICKY;
    }
}
