package com.vaibhav.foody.OrderManagement;
import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.vaibhav.foody.Classes.Dish;
import com.vaibhav.foody.Classes.Food;
import com.vaibhav.foody.Classes.Reservation;
import com.vaibhav.foody.Classes.Restaurant;
import com.vaibhav.foody.MyDatabaseReference;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Class that manages the order of the customer
 */
public class Order implements Serializable {

   private HashMap<String, Food> selectedFoods;
   private List<Food> dishes;
   private Double totalPrice;
   private String customerID;
   private String date;
   private String status;
   private String restaurantID;
   private Restaurant r;
   private String time;
   private int totalQuantity;

   private HashMap<String, MyDatabaseReference> dbReferenceList;

    public Order(String currentUserID) {
        this.totalPrice=0.0;
        selectedFoods=new HashMap<>();
        status = "New Order";
        customerID = currentUserID;
        dbReferenceList= new HashMap<>();
        this.totalQuantity = 0;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void updateTotalPrice(){
       totalPrice = 0.0;
       if(!selectedFoods.isEmpty()){
         for(Food f:selectedFoods.values()){
           totalPrice+=f.getPrice()*f.getSelectedQuantity();
        }
       totalPrice += r.getDeliveryCost();
       }
   }

    public String getCustomerID() {
        return customerID;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void addFoodToOrder(Food f){
        this.selectedFoods.put(f.getFoodID(),f);
    }
    public void removeFoodFromOrder(Food f){
        this.selectedFoods.remove(f.getFoodID());
    }

    @Exclude
    public HashMap<String,Food> getSelectedFoods(){
        return selectedFoods;
    }

    public String getRestaurantID() {
        return restaurantID;
    }

    public void setRestaurantID(String restaurantID) {
        this.restaurantID = restaurantID;
    }

    public void setR(Restaurant r) {
        this.r = r;
    }

    public String getDate(){
        return date;
    }


    public void setDate(String date) {
        this.date = date;
    }

    public String getTime(){
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setCustomerID(String customerID) {
        this.customerID = customerID;
    }

    public HashMap<String, MyDatabaseReference> getDbReferenceList() {
        return dbReferenceList;
    }

    /**
     * This method upload the order on database
     */
    public void uploadOrder() {
        /*
         * Update counter in restaurant menu -> for each dish (to compute the most popular foods)
         */
        for(final Food f : dishes) {
            DatabaseReference referenceMenu = FirebaseDatabase.getInstance()
                    .getReference("restaurants/" + restaurantID + "/Menu/"+f.getFoodID());
            dbReferenceList.put("food", new MyDatabaseReference(referenceMenu));

            dbReferenceList.get("food").setSingleValueListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    int counter= Integer.parseInt(dataSnapshot.child("PopularityCounter").getValue().toString());
                    counter += f.getSelectedQuantity();
                    FirebaseDatabase.getInstance()
                            .getReference("restaurants/" + restaurantID + "/Menu/"+f.getFoodID()+"/PopularityCounter")
                            .setValue(counter);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        }

        /**
         * UPLOAD order into restaurant table
         */
        DatabaseReference dbReferenceRestaurant = FirebaseDatabase.getInstance().getReference("restaurants");
        DatabaseReference reservationRestaurant =  dbReferenceRestaurant.child(this.getRestaurantID())
                                                        .child("reservations")
                                                        .push();

        Order o = new Order(customerID);
        o.setTime(this.time);
        o.setTotalPrice(this.totalPrice);
        o.setCustomerID(this.customerID);
        o.setRestaurantID(this.restaurantID);
        o.setTotalQuantity(this.totalQuantity);

        //Uploading order in restaurant reservations
        reservationRestaurant.setValue(o);
        reservationRestaurant.child("date").setValue(ServerValue.TIMESTAMP);
        reservationRestaurant.child("status").child("it").setValue("Nuovo ordine");
        reservationRestaurant.child("status").child("en").setValue("New order");
        reservationRestaurant.child("dishes").setValue(this.getDishes());

        /*
         * Add order to customer order history
         */
        final String[] restaurantName = new String[1];
        final String orderID= reservationRestaurant.getKey();
        final List<Food> dishes= this.dishes;
        final String time= this.time;
        final Double totalPrice= this.totalPrice;
        final String customerID= this.customerID;

        DatabaseReference referenceRestaurantOfReservation = dbReferenceRestaurant.child(this.getRestaurantID());
        dbReferenceList.put("restaurant", new MyDatabaseReference(referenceRestaurantOfReservation));

        dbReferenceList.get("restaurant").setSingleValueListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                    if(dataSnapshot.exists())
                        restaurantName[0] = dataSnapshot.child("Name").getValue().toString();
                    List<Dish> selectedDishes= new ArrayList<>();
                    String name, notes;
                    int quantity;
                    for(Food f : dishes){
                        name= f.getName();
                        quantity= f.getSelectedQuantity();
                        notes= f.getCustomerNotes();
                        selectedDishes.add(new Dish(name, quantity, notes));
                    }
                    DecimalFormat df = new DecimalFormat("#0.00");
                    String price = df.format(totalPrice);

                    Reservation reservation= new Reservation(orderID, restaurantName[0], "", time, price);
                    DatabaseReference referenceCustomer= FirebaseDatabase.getInstance().getReference("customers").child(customerID);
                    referenceCustomer.child("reservations").child(orderID).setValue(reservation);
                    referenceCustomer.child("reservations").child(orderID).child("date").setValue(ServerValue.TIMESTAMP);
                    referenceCustomer.child("reservations").child(orderID).child("dishes").setValue(selectedDishes);
                    referenceCustomer.child("reservations").child(orderID).child("restaurantID").setValue(restaurantID);
                    referenceCustomer.child("reservations").child(orderID).child("reviewFlag").setValue("false");
                    referenceCustomer.child("reservations").child(orderID).child("status").child("it").setValue("Nuovo Ordine");
                    referenceCustomer.child("reservations").child(orderID).child("status").child("en").setValue("New Order");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    public void increaseToTotalQuantity(){
        totalQuantity +=1;
    }
    public void decreaseToTotalQuantity(){
        totalQuantity -=1;
    }

    @Exclude
    public List<Food> getDishes() {
        return dishes;
    }

    @Exclude
    public void setDishes() {
        dishes = new ArrayList<>(this.selectedFoods.values());
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }
}
