package com.wehack.syncedQ;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class LLActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listen_later_activity);
        LLQueue.get();
    }
}