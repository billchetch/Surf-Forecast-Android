package com.bulan_baru.surf_forecast;

import android.app.Dialog;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.bulan_baru.surf_forecast.data.Location;
import net.chetch.appframework.GenericDialogFragment;


public class LocationDialogFragment extends GenericDialogFragment {
    public Location location;

    LocationDialogFragment(){
        //default
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        inflateContentView(R.layout.location_dialog);

        //Add location data
        TextView tv = contentView.findViewById(R.id.locationTitle);
        tv.setText(location.getLocation());

        tv = contentView.findViewById(R.id.locationDescription);
        String desc = location.getDescription();
        if(desc == null || desc == "")desc = "There is currently no info for this spot.  Please ask the surf guide.";
        tv.setText(desc);

        //set the close button
        Button closeButton = contentView.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        // Create the AlertDialog object and return it
        return createDialog();
    }
}
