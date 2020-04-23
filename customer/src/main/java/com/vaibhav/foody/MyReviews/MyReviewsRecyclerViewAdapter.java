package com.vaibhav.foody.MyReviews;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.vaibhav.foody.Classes.Rating;
import com.vaibhav.foody.R;

import java.util.List;

/**
 * RecyclerView Adapter for MyReviewsFragment
 */
public class MyReviewsRecyclerViewAdapter extends RecyclerView.Adapter<MyReviewsRecyclerViewAdapter.MyReviewViewHolder> {

    private List<Rating> reviewList;
    private Context context;

    private Toast myToast;

    public static class MyReviewViewHolder extends RecyclerView.ViewHolder{

        public RatingBar ratingBar;
        public TextView restaurantName, date, comment;
        public View itemView;

        public MyReviewViewHolder(View itemView){
            super(itemView);
            this.itemView=itemView;
            this.ratingBar = (RatingBar) itemView.findViewById(R.id.rating_bar_review);
            this.restaurantName = (TextView) itemView.findViewById(R.id.restaurant_data_tv);
            this.date = (TextView) itemView.findViewById(R.id.date_review_tv);
            this.comment = (TextView) itemView.findViewById(R.id.customer_comment);
        }
    }

    MyReviewsRecyclerViewAdapter(Context context, List<Rating> reviewList){
        this.reviewList = reviewList;
        this.context = context;
        if(context != null)
            myToast = Toast.makeText(context, "", Toast.LENGTH_SHORT);

    }

    @Override
    public MyReviewViewHolder onCreateViewHolder(ViewGroup parent, int viewType){
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        final View listItem = layoutInflater.inflate(R.layout.fragment_my_reviews_child,parent, false);
        final MyReviewViewHolder viewHolder = new MyReviewViewHolder(listItem);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull MyReviewViewHolder reviewViewHolder, int position) {


        String restaurantName = reviewList.get(position).getRestaurantName();
        reviewViewHolder.restaurantName.setText(restaurantName);
        reviewViewHolder.ratingBar.setRating(reviewList.get(position).getRate());
        reviewViewHolder.date.setText(reviewList.get(position).getDate());
        reviewViewHolder.comment.setText(reviewList.get(position).getComment());

    }

    @Override
    public int getItemCount() {
        return reviewList.size();
    }
}
