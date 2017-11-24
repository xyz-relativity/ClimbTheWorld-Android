package com.ar.opentopo.opentopoar;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.ar.opentopo.opentopoar.ViewTopoActivity.ViewTopoActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClickButtonExit(View v)
    {
        System.exit(0);
    }

    public void onClickButtonViewTopo(View v)
    {
        Intent intent = new Intent(MainActivity.this, ViewTopoActivity.class);
        startActivity(intent);
    }
}
