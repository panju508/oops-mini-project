package com.vaibhav.foody;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;

/**
 * Class that check if user has enabled wifi or mobile-data for internet connection
 */
public class ConnectionManager {

    public ConnectionManager(){
        //Empty Constructor
    }

    /**
     * This method check if connection with mobile data or wifi
     * is available
     * @param context
     * @return
     */
    public boolean haveNetworkConnection(Context context) {
        boolean haveConnectedWifi = false;
        boolean haveConnectedMobile = false;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm!=null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) {
                //Connected to the internet
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
                    haveConnectedWifi = true;
                if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
                    haveConnectedMobile = true;
            }
        }
        return haveConnectedWifi || haveConnectedMobile;
    }

    public void showDialog(final Context context)
    {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.internet_title));
        builder.setMessage(context.getString(R.string.internet_message))
                .setCancelable(false)
                .setPositiveButton(context.getString(R.string.interner_confirm), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //Sending the user to data settings
                        context.startActivity(new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS));
                        dialog.cancel();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }
}
