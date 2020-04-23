package com.vaibhav.foody;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.vaibhav.foody.LocationService.BackgroundLocationService;
import com.onesignal.OneSignal;


/**
 * This class is the NavigatorActivity that allows to move from one fragment to another
 */
public class NavigatorActivity extends AppCompatActivity {

    private NavController navController;
    private BottomNavigationView navigation;

    private String currentUserID;
    private FirebaseAuth mAuth;
    private AlertDialog.Builder myBuilder;
    private AlertDialog myAlert;
    private ConnectionManager connectionManager;
    private LocationManager manager;

    /**
     * Broadcast receiver to check if network connection is turned off
     */
    BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (!connectionManager.haveNetworkConnection(context))
                connectionManager.showDialog(context);
        }
    };

    /**
     * Broadcast receiver to check if gps is turned off
     */
    BroadcastReceiver gpsLocationReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().matches("android.location.PROVIDERS_CHANGED")) {
                if ( (!manager.isProviderEnabled( LocationManager.GPS_PROVIDER )) && (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) ) {
                    buildAlertMessageNoGps();
                }
                else {
                    if (myBuilder != null && myAlert != null) {
                        myAlert.dismiss();
                        myBuilder = null;
                        myAlert = null;
                    }
                }
        }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.navigator_layout);
        connectionManager = new ConnectionManager();

         manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserID = currentUser.getUid();
        if (currentUserID == null)
            logout();

        OneSignal.startInit(this)
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(true)
                .init();

        OneSignal.setSubscription(true);
        OneSignal.sendTag("User_ID", currentUserID);

        DatabaseReference dbReference = FirebaseDatabase.getInstance().getReference("deliveryman").child(currentUserID).child("IsActive");
        dbReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists())
                    if (dataSnapshot.getValue().toString().equals("true")) {
                        askPermission();
                    }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        navigation = (BottomNavigationView) findViewById(R.id.navigation);

        navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupWithNavController(navigation, navController);

        /** Custom action bar to hide back Icon */
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.pendingReservations_id,
                R.id.rides_id,
                R.id.routes_id,
                R.id.statistics_id,
                R.id.mainProfile_id).build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

    }

    @Override
    protected void onResume() {
        super.onResume();
        //Check if gps is enabled
        if ( (!manager.isProviderEnabled( LocationManager.GPS_PROVIDER )) && (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) ) {
            buildAlertMessageNoGps();
        }
    }

    private void buildAlertMessageNoGps() {
        if(myBuilder == null) {
            myBuilder = new AlertDialog.Builder(this);

            myBuilder.setMessage("To have in-app road directions you must enable GPS with 'Battery saving' or 'High accuracy' location method")
                    .setCancelable(false)
                    .setPositiveButton("Location Setting", new DialogInterface.OnClickListener() {
                        public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            myBuilder= null;
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            myBuilder= null;
                            dialog.cancel();
                        }
                    });
            myAlert = myBuilder.create();
            myAlert.show();

        }
    }
    private void logout() {
        //logout
        Log.d("matte", "Logout");
        FirebaseAuth.getInstance().signOut();
        OneSignal.setSubscription(false);

        //go to login
        //Navigation.findNavController(view).navigate(R.id.action_mainProfile_id_to_signInActivity); TODO mich
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
        registerReceiver(gpsLocationReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    @Override
    protected void onStop() {
        unregisterReceiver(networkReceiver);
        unregisterReceiver(gpsLocationReceiver);
        super.onStop();
    }


    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp();
    }

    private void askPermission() {
        checkLocationPermission();
    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Localization")
                        .setMessage("Allow to PolEATo to use your localization?")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(NavigatorActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            startService(new Intent(this, BackgroundLocationService.class));
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        startService(new Intent(this, BackgroundLocationService.class));
                    }

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                return;
            }

        }
    }

    /**
     * This method hides keyboard if open
     * @param activity
     */
    public static void hideKeyboard(Activity activity) {
        InputMethodManager inputManager = (InputMethodManager) activity
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        // check if no view has focus:
        View currentFocusedView = activity.getCurrentFocus();
        if (currentFocusedView != null) {
            inputManager.hideSoftInputFromWindow(((View) currentFocusedView).getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent((this), BackgroundLocationService.class));
        super.onDestroy();
    }
}

