package com.vaibhav.foody.OrderManagement.CartManagement;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.vaibhav.foody.ConnectionManager;
import com.vaibhav.foody.Classes.Food;
import com.vaibhav.foody.Interface;
import com.vaibhav.foody.MyDatabaseReference;
import com.vaibhav.foody.OrderManagement.Order;
import com.vaibhav.foody.R;
import com.vaibhav.foody.TimePickerFragment;
import com.onesignal.OneSignal;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;


/**
 * Activity related to cart
 * Here the user can still change is choices in terms of quantity
 * select the delivery time and finally confirm the order
 */
public class CartActivity extends AppCompatActivity implements Interface,TimePickerDialog.OnTimeSetListener {

    private Order order;
    private boolean flag=false;
    private static TextView tvTotal;
    private static TextView tvEmptyCart;
    private CartRecyclerViewAdapter recyclerAdapter;
    private Button orderBtn;
    private RecyclerView.LayoutManager layoutManager;
    private static RecyclerView rv;
    private List<Food> foodList;
    private Toast myToast;
    private EditText time;

    private String currentUserID;
    private FirebaseAuth mAuth;
    private ConnectionManager connectionManager;

    /**
     * Broadcast receiver invoked when there is a change in connection settings
     */
    BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(!connectionManager.haveNetworkConnection(context))
                connectionManager.showDialog(context);
        }
    };

    private HashMap<String, MyDatabaseReference> dbReferenceList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cart_layout);

        connectionManager = new ConnectionManager();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserID = currentUser.getUid();

        dbReferenceList= new HashMap<>();

        myToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);


        // OneSignal is used to send notifications between applications

        OneSignal.startInit(this)
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(true)
                .init();

        OneSignal.setSubscription(true);

        OneSignal.sendTag("User_ID", currentUserID);

        //Get order from bundle
        order = (Order) getIntent().getSerializableExtra("order");
        //Get list of foods from the order
        foodList = new ArrayList<>(order.getSelectedFoods().values());

        tvTotal = (TextView) findViewById(R.id.tv_total);
        tvEmptyCart = (TextView) findViewById(R.id.empty_cart);

        time= findViewById(R.id.input_time);
        if(order.getTime() != null && (!order.getTime().equals("")))
            time.setText(order.getTime());

        time.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogFragment timePicker = new TimePickerFragment();
                ((TimePickerFragment) timePicker).setListener(CartActivity.this);
                timePicker.show(getSupportFragmentManager(), "time picker");
            }
        });

        orderBtn = (Button) findViewById(R.id.btn_placeorder);
        rv = (RecyclerView) findViewById(R.id.recycler_cart);
        rv.setHasFixedSize(true);

        layoutManager = new LinearLayoutManager(this);
        rv.setLayoutManager(layoutManager);

        this.recyclerAdapter = new CartRecyclerViewAdapter(getApplicationContext(), foodList, order);
        rv.setAdapter(recyclerAdapter);

        DividerItemDecoration itemDecoration = new DividerItemDecoration(getApplicationContext(), 1 );
        rv.addItemDecoration(itemDecoration);

        //if object order has no food change the layout
        if(order.getSelectedFoods().isEmpty()) {
            rv.setVisibility(View.GONE);
            tvEmptyCart.setVisibility(View.VISIBLE);
        }else {
            rv.setVisibility(View.VISIBLE);
            tvEmptyCart.setVisibility(View.GONE);

        }

        //compute the total of the Order
        computeTotal(order.getTotalPrice());
        orderBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean wrongField= false;
                if(order.getSelectedFoods().isEmpty()){
                    wrongField= true;
                    myToast.setText(getString(R.string.empty_cart));
                    myToast.show();
                }

                if(time.getText().toString().equals("")) {
                    wrongField=true;
                    myToast.setText(getString(R.string.specify_time));
                    myToast.show();
                    time.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.border_wrong_field));
                }



                if(!wrongField){
                    AlertDialog.Builder builder = new AlertDialog.Builder(CartActivity.this);
                    builder.setTitle(getString(R.string.confirm_order));
                    builder.setMessage(getString(R.string.order_proceed));
                    builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            /**
                             * Add order into restaurant DB and into customer DB (order history)
                             */
                            order.setDishes();
                            order.setTime(time.getText().toString());
                            order.setStatus(getApplicationContext().getString(R.string.new_order));
                            order.uploadOrder();

                            /**
                             * Send notification to restaurant: NEW ORDER
                             */
                            sendNotification();

                            Intent returnIntent = new Intent();
                            setResult(Activity.RESULT_OK, returnIntent);
                            flag = true;
                            finish();
                        }
                    });

                    builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                        }
                    });

                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        //Receiver to check if connection is enabled
        registerReceiver(networkReceiver,filter);
    }

    /**
     *  Function used to send Notification
     */
    private void sendNotification() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                int SDK_INT = android.os.Build.VERSION.SDK_INT;
                if (SDK_INT > 8) {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                            .permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    String send_email;

                    //This is a Simple Logic to Send Notification different Device Programmatically....
                    send_email= order.getRestaurantID();

                    try {
                        String jsonResponse;

                        URL url = new URL("https://onesignal.com/api/v1/notifications");
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setUseCaches(false);
                        con.setDoOutput(true);
                        con.setDoInput(true);

                        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        con.setRequestProperty("Authorization", "Basic YjdkNzQzZWQtYTlkYy00MmIzLTg0NDUtZmQ3MDg0ODc4YmQ1");
                        con.setRequestMethod("POST");
                        String strJsonBody = "{"
                                + "\"app_id\": \"a2d0eb0d-4b93-4b96-853e-dcfe6c34778e\","

                                + "\"filters\": [{\"field\": \"tag\", \"key\": \"User_ID\", \"relation\": \"=\", \"value\": \"" + send_email + "\"}],"

                                + "\"data\": {\"Order\": \"PolEATo\"},"
                                + "\"contents\": {\"en\": \"New order\"}"
                                + "}";


                        System.out.println("strJsonBody:\n" + strJsonBody);

                        byte[] sendBytes = strJsonBody.getBytes("UTF-8");
                        con.setFixedLengthStreamingMode(sendBytes.length);

                        OutputStream outputStream = con.getOutputStream();
                        outputStream.write(sendBytes);

                        int httpResponse = con.getResponseCode();
                        System.out.println("httpResponse: " + httpResponse);

                        if (httpResponse >= HttpURLConnection.HTTP_OK
                                && httpResponse < HttpURLConnection.HTTP_BAD_REQUEST) {
                            Scanner scanner = new Scanner(con.getInputStream(), "UTF-8");
                            jsonResponse = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                            scanner.close();
                        } else {
                            Scanner scanner = new Scanner(con.getErrorStream(), "UTF-8");
                            jsonResponse = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                            scanner.close();
                        }
                        System.out.println("jsonResponse:\n" + jsonResponse);

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        if(!flag){
            //Order can be modified, pass the object to Order Activity
            Intent resultIntent= new Intent();
            resultIntent.putExtra("old_order", order);
            setResult(Activity.RESULT_CANCELED,resultIntent);
        }
        super.onBackPressed();

    }

    @Override
    public Order getOrder() {
        return order;
    }

    @Override
    public void setQuantity(int quantity) {

    }

    public static void computeTotal(Double price){
        DecimalFormat decimalFormat = new DecimalFormat("#0.00");
        String priceStr = decimalFormat.format(price).toString()+"€";
        tvTotal.setText(priceStr);

        if(price==0){
            rv.setVisibility(View.GONE);
            tvEmptyCart.setVisibility(View.VISIBLE);
        }
        else{
            rv.setVisibility(View.VISIBLE);
            tvEmptyCart.setVisibility(View.GONE);
        }
    }

    /**
     *  Function to set time inside timePicker
     */
    @Override
    public void onTimeSet(TimePicker timePicker, final int hourOfDay, final int minute) {
        final String hourStr;
        final String minStr;

        final Date date = new Date();
        final SimpleDateFormat format = new SimpleDateFormat("HH::mm");
        //Select ITALY time zone
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"), Locale.ITALY);
        final boolean[] timeok = {true};

        final int[] serverHour = new int[1];
        final int[] serverMinute = new int[1];


        final int[] openingHour = new int[1];
        final int[] openingMinute = new int[1];
        final int[] closingHour = new int[1];
        final int[] closingMinute = new int[1];

        //convert to format HH:mm
        if (hourOfDay < 10)
            hourStr = "0" + hourOfDay;
        else
            hourStr = "" + hourOfDay;

        if (minute < 10)
            minStr = "0" + minute;
        else
            minStr = "" + minute;

        //Get closing hour of restaurant
//        DatabaseReference closureReference = FirebaseDatabase.getInstance().getReference("restaurants/" + order.getRestaurantID());
//        dbReferenceList.put("close", new MyDatabaseReference(closureReference));
//
//        dbReferenceList.get("close").setSingleValueListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//
//                String[]opening = dataSnapshot.child("Open").getValue().toString().split(":");
//                openingHour[0] = Integer.parseInt(opening[0]);
//                openingMinute[0] = Integer.parseInt(opening[1]);
//
//                String[]closure = dataSnapshot.child("Close").getValue().toString().split(":");
//                closingHour[0] = Integer.parseInt(closure[0]);
//                closingMinute[0] = Integer.parseInt(closure[1]);
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//
//            }
//        });
//
//        FirebaseDatabase.getInstance().getReference("time").setValue(ServerValue.TIMESTAMP);
//        DatabaseReference timeReference = FirebaseDatabase.getInstance().getReference("time");
//        dbReferenceList.put("time", new MyDatabaseReference(timeReference));
//
//        //check if hour selected by the user is between server time and closing time
//        dbReferenceList.get("time").setSingleValueListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                   Long hourInMillis = Long.parseLong(dataSnapshot.getValue().toString());
//                   calendar.setTimeInMillis(hourInMillis);
//                   serverHour[0] = calendar.get(Calendar.HOUR_OF_DAY);
//                   serverMinute[0] = calendar.get(Calendar.MINUTE);
//
//
////
////                   if(hourOfDay<serverHour[0] && hourOfDay!=0 ){
////                       myToast.setText("You can't go back in time");
////                       myToast.show();
////                       timeok[0] = false;
////                   }
////                   else if(hourOfDay == serverHour[0]){
////                       if(minute<serverMinute[0]) {
////                           myToast.setText("You can't go back in time");
////                           myToast.show();
////                           timeok[0] = false;
////                       }
////                   }
////                   else if(hourOfDay>closingHour[0] && closingHour[0]!=0) {
////                       myToast.setText("Restaurant is closed at that moment");
////                       myToast.show();
////                       timeok[0] = false;
////                   }
////                    else if(hourOfDay == closingHour[0])
////                        if(minute > closingMinute[0]) {
////                            myToast.setText("Restaurant is closed at that moment");
////                            myToast.show();
////                            timeok[0] = false;
////                        }
//                    if(timeok[0]){
//                       time.setText(hourStr +":"+minStr);
//                       order.setTime(time.getText().toString());
//                   }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//            }
//        });

        time.setText(hourStr +":"+minStr);
        order.setTime(time.getText().toString());
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(networkReceiver);
        for(MyDatabaseReference my_ref : dbReferenceList.values())
            my_ref.removeAllListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for(MyDatabaseReference my_ref : dbReferenceList.values())
            my_ref.removeAllListener();
    }
}
