package com.bulan_baru.surf_forecast;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.bulan_baru.surf_forecast_data.Digest;
import com.bulan_baru.surf_forecast_data.utils.UncaughtExceptionHandler;

public class UCEActivity extends GenericActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        includeLocation = false;
        super.onCreate(null);

        setContentView(R.layout.activity_uce);

        String report = getIntent().getStringExtra(UncaughtExceptionHandler.REPORT);

        Button closeButton = findViewById(R.id.uceCloseButton);
        closeButton.setEnabled(false);
        closeButton.setOnClickListener(this);

        Digest digest = new Digest("Uncaught Exception Report", report);
        viewModel.postDigest(digest).observe(this, t->{
            closeButton.setEnabled(true);
        });

        TextView tv = findViewById(R.id.uceErrorReport);
        tv.setText(report);
    }

    @Override
    public void onClick(View v) {
        finish();
    }
}
