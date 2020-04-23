package com.vaibhav.foody.RestaurantSearch;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.vaibhav.foody.MyDatabaseReference;
import com.vaibhav.foody.OrderManagement.OrderActivity;
import com.vaibhav.foody.R;
import com.vaibhav.foody.Classes.Restaurant;
import com.squareup.picasso.Picasso;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


/**
 * This is the adapter for the restaurant search list
 */
public class RestaurantRecyclerViewAdapter extends RecyclerView.Adapter<RestaurantRecyclerViewAdapter.RestaurantViewHolder> {


    private List<Restaurant> list; //current displayed list
    private Context context;
    private Toast myToast;

    private String currentUserID;
    private FirebaseAuth mAuth;

    private HashMap<String, MyDatabaseReference> dbReferenceList;

    /**
     * current order state of the list
     */
    private enum State{
        INIT,
        NAME_SORTED,
        PRICE_SORTED,
        PRICE_INVERSE_SORTED,
        DELIVERY_SORTED,
        STAR_SORTED
    }
    private State currState;

    /**
     * Comparators
     */

    private SortByName nameComparator;
    private SortByPrice priceComparator;
    private SortByPriceInverse priceInverseComparator;
    private SortByDelivery deliveryComparator;
    private SortByStar starComparator;


    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class RestaurantViewHolder extends RecyclerView.ViewHolder {

        public TextView title, type, delivery, priceRange, open, reviews_counter;
        private RatingBar ratingBar;
        public ImageView img;
        public View itemView;

        // attributes for togglebutton favorite
        public ScaleAnimation scaleAnimation;
        public BounceInterpolator bounceInterpolator;
        public ToggleButton buttonFavorite;

        public RestaurantViewHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;

            this.title = (TextView) itemView.findViewById(R.id.textViewTitle);
            this.type = (TextView) itemView.findViewById(R.id.textViewType);
            this.open = (TextView) itemView.findViewById(R.id.textViewOpen);
            this.img = (ImageView) itemView.findViewById(R.id.imageView);
            this.delivery = (TextView) itemView.findViewById(R.id.textViewDelivery);
            this.priceRange = (TextView) itemView.findViewById(R.id.textViewPriceRange);
            this.ratingBar = (RatingBar) itemView.findViewById(R.id.rating_bar);
            this.reviews_counter = (TextView) itemView.findViewById(R.id.reviews_counter_tv);

            // initialize attributes for favorite toggle
            this.buttonFavorite= (ToggleButton) itemView.findViewById(R.id.button_favorite);
            scaleAnimation = new ScaleAnimation(0.7f, 1.0f, 0.7f, 1.0f, Animation.RELATIVE_TO_SELF, 0.7f, Animation.RELATIVE_TO_SELF, 0.7f);
            scaleAnimation.setDuration(500);
            bounceInterpolator = new BounceInterpolator();
            scaleAnimation.setInterpolator(bounceInterpolator);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public RestaurantRecyclerViewAdapter(Context context, List<Restaurant> list) {

        this.list = list;
        this.context = context;
        currState = State.INIT;

        if(context != null)
            myToast = Toast.makeText(context, "", Toast.LENGTH_SHORT);

        this.nameComparator = new SortByName();
        this.priceComparator = new SortByPrice();
        this.priceInverseComparator = new SortByPriceInverse();
        this.deliveryComparator = new SortByDelivery();
        this.starComparator = new SortByStar();

        this.dbReferenceList= new HashMap<>();
    }

    public void updateAdapterList(List<Restaurant>list){
        this.list=list;
    }
    // Create new views (invoked by the layout manager)
    @Override
    public RestaurantRecyclerViewAdapter.RestaurantViewHolder onCreateViewHolder(ViewGroup parent,
                                                                                 int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        final View listItem = layoutInflater.inflate(R.layout.restaurant_item, parent, false);
        final RestaurantViewHolder viewHolder = new RestaurantViewHolder(listItem);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserID = currentUser.getUid();

        return viewHolder;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final RestaurantViewHolder holder, final int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.title.setText(list.get(position).getName());
        holder.type.setText(list.get(position).getType());

        holder.ratingBar.setRating((list.get(position).getAvgStars().floatValue()));
        String reviews = "(" + list.get(position).getTotalReviews() + ")";
        holder.reviews_counter.setText(reviews);
        if(list.get(position).getImage().equals("")){
            Picasso.with(context).load(R.drawable.plate_fork).into(holder.img);
        }
        else
            Picasso.with(context).load(list.get(position).getImage()).into(holder.img);
        DecimalFormat decimalFormat = new DecimalFormat("#.00"); //two decimal
        double deliveryCost = list.get(position).getDeliveryCost();
        if(deliveryCost < 0.1)
            holder.delivery.setText(context.getString(R.string.delivery_cost)+":\n"+context.getString(R.string.free_delivery));
        else{
            String priceStr = decimalFormat.format(list.get(position).getDeliveryCost());
            holder.delivery.setText(context.getString(R.string.delivery_cost)+":\n"+priceStr+"€");
        }

        int priceR = list.get(position).getPriceRange();
        String stringR = new String("");
        for(int count = 0; count < priceR; count ++)
            stringR += "$";

        holder.priceRange.setText(context.getString(R.string.price_range)+":\n"+stringR);


        if(list.get(position).getIsOpen())
        {
            holder.open.setText(context.getString(R.string.open));
            holder.open.setTextColor(Color.rgb(0, 100, 0));
        }
        else{
            holder.open.setText(context.getString(R.string.close));
            holder.open.setTextColor(Color.rgb(150, 0, 0));
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(list.get(position).getIsOpen()) {
                    Log.d("matte", "OnClick | restaurant ID: " + list.get(position).getId());
                    Bundle bundle = new Bundle();
                    bundle.putString("id", list.get(position).getId());
                    bundle.putString("name",list.get(position).getName());
                    Intent menuIntent = new Intent(context, OrderActivity.class);
                    menuIntent.putExtras(bundle);
                    context.startActivity(menuIntent);
                }
                else
                {
                    myToast.setText(context.getString(R.string.closed_restaurant));
                    myToast.show();
                }
            }
        });

        // animate button favorite
        DatabaseReference reference= FirebaseDatabase.getInstance().getReference("customers/"+currentUserID+"/Favorite");
        dbReferenceList.put("favorite", new MyDatabaseReference(reference));

        dbReferenceList.get("favorite").setValueListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()) {
                    if(position<list.size()) {
                        String restaurantID = list.get(position).getId();
                        if (dataSnapshot.hasChild(restaurantID))
                            holder.buttonFavorite.setChecked(true);
                        else
                            holder.buttonFavorite.setChecked(false);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        holder.buttonFavorite.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                //animation
                compoundButton.startAnimation(holder.scaleAnimation);

                // add or remove restaurant from favorite
                if(isChecked){
                    // add restaurant to favorite list
                    final String restaurantID= list.get(position).getId();
                    DatabaseReference refFavoriteRestaurant= FirebaseDatabase.getInstance()
                                                                    .getReference("customers/" + currentUserID
                                                                                       +"/Favorite/");
                    dbReferenceList.put("favoriteRest", new MyDatabaseReference(refFavoriteRestaurant));

                    dbReferenceList.get("favoriteRest").setSingleValueListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if(!dataSnapshot.hasChild(restaurantID)){
                                FirebaseDatabase.getInstance()
                                        .getReference("customers/"+currentUserID+"/Favorite/"+restaurantID+"/dishes")
                                        .setValue("none");
                            }
                            else if(dataSnapshot.child(restaurantID).getChildrenCount()==0){
                                FirebaseDatabase.getInstance()
                                        .getReference("customers/"+currentUserID+"/Favorite/"+restaurantID+"/dishes")
                                        .setValue("none");
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
                else{
                    // remove restaurant from favorite list
                    String restaurantID= list.get(position).getId();
                    FirebaseDatabase.getInstance()
                            .getReference("customers/"+currentUserID+"/Favorite/"+restaurantID)
                            .removeValue();
                    holder.buttonFavorite.setChecked(false);
                }
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return list.size();
    }

    public void display(List<Restaurant> list) {
        this.list = list;
        notifyDataSetChanged();
    }

    public void sortByName(){
        Collections.sort(this.list, this.nameComparator);
        currState = State.NAME_SORTED;
        notifyDataSetChanged();
    }

    public void sortByPrice(){
        Collections.sort(this.list, this.priceComparator);
        currState = State.PRICE_SORTED;
        notifyDataSetChanged();
    }

    public void sortByPriceInverse(){
        Collections.sort(this.list, this.priceInverseComparator);
        currState = State.PRICE_INVERSE_SORTED;
        notifyDataSetChanged();
    }

    public void sortByDelivery(){
        Collections.sort(this.list, this.deliveryComparator);
        currState = State.DELIVERY_SORTED;
        notifyDataSetChanged();
    }

    public void sortByStar(){
        Collections.sort(this.list, this.starComparator);
        currState = State.STAR_SORTED;
        notifyDataSetChanged();
    }

    /* TODO optimize it by using TreeMap with ordered insertion, without sort again every new insertion */
    public void updateLayout(){
        if(currState == State.INIT){
            notifyDataSetChanged();
        }
        else if(currState == State.NAME_SORTED){
            sortByName();
        }
        else if(currState == State.PRICE_SORTED){
            sortByPrice();
        }
        else if(currState == State.PRICE_INVERSE_SORTED){
            sortByPriceInverse();
        }
        else if(currState == State.DELIVERY_SORTED){
            sortByDelivery();
        }
        else if(currState == State.STAR_SORTED){
            sortByStar();
        }

    }

    private class SortByName implements Comparator<Restaurant>{

        @Override
        public int compare(Restaurant r1, Restaurant r2) {
            return r1.getName().compareTo(r2.getName());
        }
    }

    private class SortByPrice implements  Comparator<Restaurant>{

        @Override
        public int compare(Restaurant r1, Restaurant r2) {
            return r1.getPriceRange() - r2.getPriceRange();
        }
    }

    private class SortByPriceInverse implements Comparator<Restaurant>{

        @Override
        public int compare(Restaurant r1, Restaurant r2) {
            return r2.getPriceRange() - r1.getPriceRange();
        }
    }

    private class SortByDelivery implements Comparator<Restaurant>{
        @Override
        public int compare(Restaurant r1, Restaurant r2) {
            return Double.compare(r1.getDeliveryCost(), r2.getDeliveryCost());
        }
    }

    private class SortByStar implements Comparator<Restaurant>{

        @Override
        public int compare(Restaurant r1, Restaurant r2) {
            if(r1.getAvgStars().isNaN())
                return 1;
            if(r2.getAvgStars().isNaN())
                return -1;
            if(r1.getAvgStars().isNaN() && r2.getAvgStars().isNaN())
                return r1.getName().compareTo(r2.getName()); // if both haven't ratings, sort by name

            return Double.compare(r1.getAvgStars(), r2.getAvgStars()) * -1;
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        for (MyDatabaseReference my_ref : dbReferenceList.values())
            my_ref.removeAllListener();
    }
}
