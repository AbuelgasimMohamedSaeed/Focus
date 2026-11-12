package io.github.abuelgasimmohamedsaeed.focus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.IBinder;

/** Plays one bell while Focus is closed, then stops. Runs only for the few seconds the bell lasts. */
public class BellService extends Service {
    private MediaPlayer player;

    static void play(Context c, String kind) {
        try {
            c.startForegroundService(new Intent(c, BellService.class).putExtra("kind", kind));
        } catch (Exception e) { // Android refused: still say that time is up, without the sound
            Sit s = Sit.load(c);
            if (s != null && Engine.END.equals(kind)) {
                c.getSystemService(NotificationManager.class).notify(Engine.ID_ALERT, Engine.alert(c, s));
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Sit s = Sit.load(this);
        String kind = intent == null ? null : intent.getStringExtra("kind");
        boolean end = Engine.END.equals(kind);
        if (s == null) s = new Sit();
        Notification n = end ? Engine.alert(this, s) : Engine.live(this, s);
        startForeground(end ? Engine.ID_ALERT : Engine.ID_LIVE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        if (kind == null || "off".equals(s.sound)) {
            done();
        } else {
            play(Engine.sound(s.sound, end), end ? 1f : Engine.START.equals(kind) ? .45f : .4f);
        }
        return START_NOT_STICKY;
    }

    private void play(int res, float volume) {
        release();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            try (AssetFileDescriptor fd = getResources().openRawResourceFd(res)) {
                player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            }
            player.setVolume(volume, volume);
            player.setOnCompletionListener(mp -> done());
            player.setOnErrorListener((mp, what, extra) -> {
                done();
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            done();
        }
    }

    /** Stop, but leave the notification on screen. */
    private void done() {
        release();
        stopForeground(STOP_FOREGROUND_DETACH);
        stopSelf();
    }

    private void release() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
