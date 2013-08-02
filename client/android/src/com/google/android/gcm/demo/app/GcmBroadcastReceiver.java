/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gcm.demo.app;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.wehack.syncedQ.LLActivity;
import com.wehack.syncedQ.LLQueue;
import com.wehack.syncedQ.R;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Handling of GCM messages.
 */
public class GcmBroadcastReceiver extends BroadcastReceiver {
    static final String TAG = "GCMDemo";
    public static final int NOTIFICATION_ID = 1;
    private NotificationManager mNotificationManager;
    NotificationCompat.Builder builder;
    Context ctx;
    @Override
    public void onReceive(Context context, Intent intent) {
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        ctx = context;
        String messageType = gcm.getMessageType(intent);
        if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
            sendNotification("Send error: " + intent.getExtras().toString());
        } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
            sendNotification("Deleted messages on server: " + intent.getExtras().toString());
        } else {
            sendNotification("Received: " + intent.getExtras().toString());
            // "urn":"soundcloud:sounds:125","finished_at":"0001-01-01T00:00:00Z","last_played_at":"0001-01-01T00:00:00Z","progress":0}


            LLQueue queue = LLQueue.get();
            if (queue == null) return;

            //queue.loadListenLaterQueue();

            if (intent.hasExtra("set")) {
                String set = intent.getStringExtra("set");
                try {
                    JSONObject obj = new JSONObject(set);
                    String urn = obj.optString("urn");
                    Log.d(TAG, "GCM set with urn:"+urn);


                    if (!TextUtils.isEmpty(urn)) {
                        queue.addUrn(urn);
                    }
                } catch (JSONException e) {
                    Log.w(TAG, e);
                }




            } else if (intent.hasExtra("delete")) {
                String delete = intent.getStringExtra("delete");
                try {
                    JSONObject obj = new JSONObject(delete);
                    String urn = obj.optString("urn");
                    Log.d(TAG, "GCM set with urn:"+urn);

                    if (!TextUtils.isEmpty(urn)) {
                        queue.removeUrn(urn);
                    }

                } catch (JSONException e) {
                    Log.w(TAG, e);
                }
            } else if(intent.hasExtra("play")) {

                String play = intent.getStringExtra("play");
                try {
                    JSONObject obj = new JSONObject(play);
                    String urn = obj.optString("urn");
                    String toggleAt = obj.optString("toggle_at");
                    long progress = obj.optInt("progress", 0);

                    Log.d(TAG, "GCM play with urn:"+urn +" ,togglet_at:"+toggleAt);

                    Intent playIntent = new Intent();

                    queue.playUrn(urn, progress);

                    if (!TextUtils.isEmpty(toggleAt)) {
                        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");//spec for RFC3339 (with fractional seconds)
                        try {
                            Date date = format.parse(toggleAt);

                            Log.d(TAG, "parsed date:"+date);

//                            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//                            alarmManager.set(AlarmManager.RTC_WAKEUP, date.getTime(), PendingIntent.getActivity(context, 0, playIntent, 0));
                        } catch (ParseException e) {
                            Log.w(TAG, e);
                        }
                    } else {



                    }








                } catch (JSONException e) {
                    Log.w(TAG, e);
                }



            }
        }
        setResultCode(Activity.RESULT_OK);
    }

    // Put the GCM message into a notification and post it.
    private void sendNotification(String msg) {
        mNotificationManager = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, LLActivity.class), 0);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(ctx)
        .setSmallIcon(R.drawable.ic_stat_gcm)
        .setContentTitle("GCM Notification")
        .setStyle(new NotificationCompat.BigTextStyle()
        .bigText(msg))
        .setContentText(msg);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }
}
