package io.github.abuelgasimmohamedsaeed.focus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** A bell is due: the start, an interval, or the end. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Engine.ACTION_ALARM.equals(i.getAction())) return;
        Engine.channels(c);
        Engine.fire(c, i.getStringExtra("kind"), i.getLongExtra("endAt", 0));
    }
}
