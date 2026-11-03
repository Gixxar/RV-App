package com.poulton.rvapp;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

/** Android schedules an approximate daily check; mileage uses the last logged reading. */
public final class ServiceNotifications extends JobService {
    private static final String CHANNEL = "rv-service";
    private static final int JOB = 2307;
    public static void schedule(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Service reminders",NotificationManager.IMPORTANCE_DEFAULT));
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler.getPendingJob(JOB) == null) scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,ServiceNotifications.class))
                .setPeriodic(24*60*60*1000L).setPersisted(true).build());
    }
    public static void refresh(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        int due;
        try (LogStore store = new LogStore(context)) { due = store.dueCount(); }
        if (due == 0) { nm.cancel(JOB); return; }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent open = new Intent(context,MainActivity.class).putExtra("show_reminders",true);
        PendingIntent intent = PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(JOB,new Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_rv).setContentTitle("RV service due")
                .setContentText(due + " service reminder(s) due. Tap to review.")
                .setContentIntent(intent).setAutoCancel(true).setOnlyAlertOnce(true).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        // Small local database only; no network or long-running work.
        refresh(this); return false;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
