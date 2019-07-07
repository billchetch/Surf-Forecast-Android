package com.bulan_baru.surf_forecast;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.bulan_baru.surf_forecast_data.Location;

public class LocationDialogFragment extends AppCompatDialogFragment {
    private Location location;

    LocationDialogFragment(Location location){
        this.location = location;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        //get the content view
        View contentView = inflater.inflate(R.layout.location_dialog, null);

        //Add location data
        TextView tv = contentView.findViewById(R.id.locationTitle);
        tv.setText(location.getLocation());

        tv = contentView.findViewById(R.id.locationDescription);
        String desc = location.getDescription();
        if(desc == null || desc == "")desc = "There is currently no info for this spot.  Please ask the surf guide.";
        tv.setText(desc);

        //set title
        builder.setView(contentView).setTitle(null);

        //set the close button
        Button closeButton = contentView.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        // Create the AlertDialog object and return it
        Dialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        return dialog;
    }
}
