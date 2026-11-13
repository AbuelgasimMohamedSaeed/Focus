package io.github.abuelgasimmohamedsaeed.focus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a restart or an update, bring back the live line and the bells of a sit that is still running. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        Engine.channels(c);
        Nightly.schedule(c);
        Sit s = Sit.load(c);
        if (s == null) return;
        Engine.refresh(c, s);
        Engine.schedule(c, s);
    }
}
