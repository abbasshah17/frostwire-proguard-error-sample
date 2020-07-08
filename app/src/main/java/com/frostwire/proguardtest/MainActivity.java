package com.frostwire.proguardtest;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private FrostWireTorrentTest frostWireTorrentTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        frostWireTorrentTest = new FrostWireTorrentTest();
        frostWireTorrentTest.start(new File(getFilesDir().getAbsolutePath()));
    }
}