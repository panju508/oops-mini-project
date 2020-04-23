package com.vaibhav.foody.DailyOffer.EditFood;

import android.app.ProgressDialog;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.PopupMenu;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.navigation.Navigation;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.vaibhav.foody.DailyOffer.DishCategoryTranslator;
import com.vaibhav.foody.DailyOffer.Food;
import com.vaibhav.foody.MyDatabaseReference;
import com.vaibhav.foody.R;
import com.vaibhav.foody.View.ViewModel.MyViewModel;
import com.onesignal.OneSignal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


import static android.app.Activity.RESULT_OK;


/**
 * The Fragment to edit a food from the menu
 */
public class EditFoodFragment extends DialogFragment {

    private Toast myToast;

    private static final int REQUEST_TAKE_PHOTO = 1;
    private static final int RESULT_LOAD_IMG = 2;
    private String currentPhotoPath;
    private View v; //this view


    private FloatingActionButton change_im;
    private BottomNavigationView navigation;
    private ImageView imageFood;

    private Map<String, ImageButton> imageButtons;
    private Map<String,EditText> editTextFields;

    private DishCategoryTranslator translator;

    //price ranges
    private int firstRange = 7;
    private int secondRange = 15;

    private String localeShort;
    private String currentUserID;
    private FirebaseAuth mAuth;

    private MyViewModel model;

    private ProgressDialog progressDialog;

    private String toModifyID, toModifyCategory;

    //to map each category name to the position inside the spinner
    private Map<String, Integer> spinnerCategoryPosition;

    private MyDatabaseReference foodReference;
    int indexReference;

    /** ***********************************
     ********   ANDROID CALLBACKS   ****
     *********************************** */


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        myToast = Toast.makeText(getActivity(), "", Toast.LENGTH_SHORT);

        //translate the food Category based on the current active Locale
        String locale = Locale.getDefault().toString();
        Log.d("matte", "LOCALE: "+locale);
        localeShort = locale.substring(0, 2);

        model = ViewModelProviders.of(getActivity()).get(MyViewModel.class);


        toModifyID = EditFoodFragmentArgs.fromBundle(getArguments()).getId();
        toModifyCategory = EditFoodFragmentArgs.fromBundle(getArguments()).getCategory();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserID = currentUser.getUid();


        OneSignal.startInit(getContext())
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(true)
                .init();

        OneSignal.setSubscription(true);
        OneSignal.sendTag("User_ID", currentUserID);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        /** Inflate the menu; this adds items to the action bar if it is present.*/
        inflater.inflate(R.menu.save_menu, menu);

        /** Button to save changes */
        menu.findItem(R.id.save_id).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                saveChanges();
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.edit_food_fragment, container, false);

        navigation = getActivity().findViewById(R.id.navigation);

        //collects the editText
        editTextFields = new HashMap<>();
        editTextFields.put("Name", (EditText) v.findViewById(R.id.nameFood));
        editTextFields.put("Description", (EditText) v.findViewById(R.id.editDescription));
        editTextFields.put("Price", (EditText) v.findViewById(R.id.editPrice));
        editTextFields.put("Quantity", (EditText) v.findViewById(R.id.editQuantity));

        //collects the X imageButtons
        imageButtons = new HashMap<>();
        imageButtons.put("Name", (ImageButton) v.findViewById(R.id.cancel_name));
        imageButtons.put("Description", (ImageButton) v.findViewById(R.id.cancel_description));
        imageButtons.put("Price", (ImageButton) v.findViewById(R.id.cancel_price));
        imageButtons.put("Quantity", (ImageButton) v.findViewById(R.id.cancel_quantity));

        //set the listener for the X imageButtons to clear the text
        for (ImageButton b : imageButtons.values()) {
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearText(v);
                }
            });
        }

        change_im = v.findViewById(R.id.frag_change_im);
        imageFood = v.findViewById(R.id.imageFood);


        //set the listener to change the image
        change_im.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeImage();
            }
        });

        //fill the fields with initial values (uses FireBase)
        if(getActivity() != null)
            progressDialog = ProgressDialog.show(getActivity(), "", getString(R.string.loading));

        fillFields();

        return v;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handleButton();
        buttonListener();
    }


    @Override
    public void onResume() {
        super.onResume();
        /** Hide bottomBar for this fragment*/
        navigation.setVisibility(View.GONE);
    }


    /**
     * Method to fill the fields of the layout by attaching the listeners to firebase and downloading data
     */
    private void fillFields(){

        //Download text infos
        foodReference = new MyDatabaseReference(FirebaseDatabase.getInstance()
                                                .getReference("restaurants/"+currentUserID+"/Menu/"+toModifyID));

        foodReference.setValueListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                if(dataSnapshot.hasChild("Name") &&
                        dataSnapshot.hasChild("Description") &&
                        dataSnapshot.hasChild("Price") &&
                        dataSnapshot.hasChild("Quantity") &&
                        dataSnapshot.hasChild("Category") &&
                        dataSnapshot.hasChild("photoUrl"))
                {
                    // it is setted to the first record (restaurant)
                    // when the sign in and log in procedures will be handled, it will be the proper one
                    if (dataSnapshot.exists()) {

                        // dataSnapshot is the "issue" node with all children
                        for (DataSnapshot snap : dataSnapshot.getChildren()) {

                            if(editTextFields.containsKey(snap.getKey())) {
                                editTextFields.get(snap.getKey()).setText(snap.getValue().toString());
                            }
                        } //for end

                        //Download the food pic
                        StorageReference storageReference = FirebaseStorage.getInstance().getReference();
                        StorageReference photoReference= storageReference
                                .child(currentUserID +"/FoodImages/"+
                                        toModifyID+".jpg");

                        final long ONE_MEGABYTE = 1024 * 1024;
                        photoReference.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                            @Override
                            public void onSuccess(byte[] bytes) {
                                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                imageFood.setImageBitmap(bmp);
                                if(progressDialog.isShowing())
                                    progressDialog.dismiss();

                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                Log.d("matte", "No image found. Default img setting");
                                //set default image if no image was set
                                imageFood.setImageResource(R.drawable.plate_fork);
                                if(progressDialog.isShowing())
                                    progressDialog.dismiss();
                            }
                        });

                    }
                } //end if

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d("matte", "onCancelled | ERROR: " + databaseError.getDetails() +
                        " | MESSAGE: " + databaseError.getMessage());
                myToast.setText(databaseError.getMessage().toString());
                myToast.show();
            }
        });
    }


    /**
     * It checks the validity of the inserted data, then it uploads them on firebase
     */
    private void saveChanges(){

        boolean wrongField= false;
        // REGEX FOR FIELDS VALIDATION BEFORE COMMIT
        String accentedCharacters = new String("àèìòùÀÈÌÒÙáéíóúýÁÉÍÓÚÝâêîôûÂÊÎÔÛãñõÃÑÕäëïöüÿÄËÏÖÜŸçÇßØøÅåÆæœ");
        String accentedString = new String("[a-zA-Z"+accentedCharacters+"0-9]+");
        // regex for compound name (e.g. L'acqua)
        String compoundName = new String(accentedString+"((\\s)?'"+"(\\s)?"+accentedString+")?");
        //strings separated by space. Start with string and end with string.
        String nameRegex = new String(compoundName+"(\\s("+compoundName+"\\s)*"+compoundName+")?");

        for (String fieldName : editTextFields.keySet()) {
            EditText field = editTextFields.get(fieldName);
            if(field != null){
                if (field.getText().toString().equals("")) {
                    Toast.makeText(getContext(), getContext().getString(R.string.empty_field), Toast.LENGTH_LONG).show();
                    field.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.border_wrong_field));
                    wrongField = true;
                } else
                    field.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.border_right_field));
            }
            else
                return;
        }
        if (!editTextFields.get("Name").getText().toString().matches(nameRegex)) {
            wrongField = true;
            myToast.setText("The name must start with letters and must end with letters. Space are allowed. Numbers are not allowed");
            myToast.show();
            editTextFields.get("Name").setBackground(ContextCompat.getDrawable(getActivity(), R.drawable.border_wrong_field));
        }
        if(!editTextFields.get("Price").getText().toString().matches("[0-9]+([\\.\\,][0-9]+)?") ){
            Toast.makeText(getContext(), getContext().getString(R.string.error_format_price), Toast.LENGTH_LONG).show();
            editTextFields.get("Price").setBackground(ContextCompat.getDrawable(getContext(), R.drawable.border_wrong_field));
            wrongField = true;
        }
        if(!editTextFields.get("Quantity").getText().toString().matches("[0-9]+") ){
            Toast.makeText(getContext(), getContext().getString(R.string.error_format_quantity), Toast.LENGTH_LONG).show();
            editTextFields.get("Quantity").setBackground(ContextCompat.getDrawable(getContext(), R.drawable.border_wrong_field));
            wrongField = true;
        }
        if(!wrongField){

            //retrieve the inserted data
            String name = editTextFields.get("Name").getText().toString();
            String description = editTextFields.get("Description").getText().toString();
            String quantity = editTextFields.get("Quantity").getText().toString();
            String priceString = editTextFields.get("Price").getText().toString().replace(",", ".");
            Bitmap img = ((BitmapDrawable) imageFood.getDrawable()).getBitmap();

            //insert the data into the DB: the category cannot change
            DatabaseReference reference = FirebaseDatabase.getInstance()
                                        .getReference("restaurants/"+currentUserID+"/Menu/"+toModifyID);
            reference.child("Name").setValue(name);
            reference.child("Description").setValue(description);
            reference.child("Quantity").setValue(quantity);
            reference.child("Price").setValue(priceString);

            Food f = new Food(toModifyID, null, name, description,
                                Double.parseDouble(priceString), Integer.parseInt(quantity));

            uploadFile(img, f);
        }
    }


    /**
     * It uploads the given bitmap of the given food on firebase Storage
     * @param bitmap
     * @param f
     */
    private void uploadFile(final Bitmap bitmap, final Food f) {
        final StorageReference storageReference = FirebaseStorage
                .getInstance()
                .getReference()
                .child(currentUserID +"/FoodImages/"+f.getId()+".jpg");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 20, baos);
        byte[] data = baos.toByteArray();

        UploadTask uploadTask = storageReference.putBytes(data);
        uploadTask
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        if(getContext() != null) {
                            storageReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {
                                    //save the link to the image
                                    final String downloadUrl =
                                            uri.toString();
                                    FirebaseDatabase.getInstance()
                                            .getReference("restaurants")
                                            .child(currentUserID + "/Menu/" + f.getId() + "/photoUrl")
                                            .setValue(downloadUrl);
                                    //set the image on the object
                                    f.setImg(bitmap);
                                }
                            });
                            // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
                            Uri downloadUrl = taskSnapshot.getUploadSessionUri();

                            String s = taskSnapshot.getMetadata().getReference().getDownloadUrl().toString();
                            Log.d("matte", "downloadUrl-->" + downloadUrl);
                            if (getActivity() != null) {
                                myToast.setText(getString(R.string.saved));
                                myToast.show();
                            }

                            /**
                             * SAVE ON MODEL_VIEW
                             */
                            model.updateChild(toModifyCategory, toModifyID, f);
                            //model.removeChild(toModifyCategory, toModifyID);
                            //model.insertChild(toModifyCategory, f);

                            //set the priceRange for the restaurant after the insertion
                            DatabaseReference reference = FirebaseDatabase.getInstance().getReference()
                                    .child("restaurants/" + currentUserID + "/PriceRange");
                            double meanPrice = model.getMeanPrice();
                            if (meanPrice == 0)
                                reference.setValue(0);
                            else if (meanPrice < firstRange)
                                reference.setValue(1);
                            else if (meanPrice < secondRange)
                                reference.setValue(2);
                            else
                                reference.setValue(3);

                            if (progressDialog.isShowing())
                                progressDialog.dismiss();

                            /**
                             * GO TO DAILY_OFFER_FRAGMENT
                             */
                            Navigation.findNavController(v).navigate(R.id.action_editFoodFragment_id_to_daily_offer_id);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // Handle unsuccessful uploads
                        Log.d("matte", "Upload failed");
                        if(getActivity() != null){
                            myToast.setText(getString(R.string.failure));
                            myToast.show();
                        }


                        /**
                         * SAVE ON MODEL_VIEW
                         */
                        //save anyway but with the default image
                        Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.plate_fork);
                        f.setImg(bmp);
                        //here insert new food even without image
                        model.updateChild(toModifyCategory, toModifyID, f);
                        /*model.removeChild(toModifyCategory, toModifyID);
                        model.insertChild(toModifyCategory, f)*/

                        //set the priceRange for the restaurant after the insertion
                        DatabaseReference reference = FirebaseDatabase.getInstance().getReference()
                                .child("restaurants/"+currentUserID+"/PriceRange");
                        double meanPrice = model.getMeanPrice();
                        if(meanPrice == 0)
                            reference.setValue(0);
                        else if(meanPrice < firstRange)
                            reference.setValue(1);
                        else if(meanPrice < secondRange)
                            reference.setValue(2);
                        else
                            reference.setValue(3);


                        if(progressDialog.isShowing())
                            progressDialog.dismiss();
                        /**
                         * GO TO ACCOUNT_FRAGMENT
                         */
                        Navigation.findNavController(v).navigate(R.id.action_editFoodFragment_id_to_daily_offer_id);
                        /**
                         *
                         */
                    }
                });

    }


    /**
     * To clear the text on the given view
     * @param view
     */
    public void clearText(View view) {
        if(view.getId() == R.id.cancel_name)
            editTextFields.get("Name").setText("");
        else if(view.getId() == R.id.cancel_description)
            editTextFields.get("Description").setText("");
        else if(view.getId() == R.id.cancel_price)
            editTextFields.get("Price").setText("");
        else if(view.getId() == R.id.cancel_quantity)
            editTextFields.get("Quantity").setText("");
    }



    public void handleButton(){
        for(ImageButton b : imageButtons.values())
            b.setVisibility(View.INVISIBLE);

        for (String fieldName : editTextFields.keySet()){
            final EditText field= editTextFields.get(fieldName);
            final ImageButton button= imageButtons.get(fieldName);
            if(field != null && button != null) {
                field.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean hasFocus) {
                        if (hasFocus)
                            showButton(field, button);
                        else
                            hideButton(button);
                    }
                });

                field.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showButton(field, button);
                    }
                });
            }
        }
    }


    public void showButton(EditText field, ImageButton button){
        if(field.getText().toString().length()>0)
            button.setVisibility(View.VISIBLE);
        else
            button.setVisibility(View.INVISIBLE);
    }


    public void hideButton(ImageButton button){
        button.setVisibility(View.INVISIBLE);
    }


    public void buttonListener(){
        EditText field;
        for (String fieldName : editTextFields.keySet()){
            field= editTextFields.get(fieldName);
            final ImageButton button= imageButtons.get(fieldName);
            if(button!=null && field != null) {
                TextWatcher textWatcher;
                field.addTextChangedListener(textWatcher= new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        if (s.toString().trim().length() == 0) {
                            button.setVisibility(View.INVISIBLE);
                        } else {
                            button.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (s.toString().trim().length() == 0) {
                            button.setVisibility(View.INVISIBLE);
                        } else {
                            button.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (s.toString().trim().length() == 0) {
                            button.setVisibility(View.INVISIBLE);
                        } else {
                            button.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
            else
                return;
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_TAKE_PHOTO) {
            if (resultCode == RESULT_OK) {
                setPic(currentPhotoPath);
            }
        }
        if (requestCode == RESULT_LOAD_IMG) {
            if (resultCode == RESULT_OK) {
                try {
                    final Uri imageUri = data.getData();
                    final InputStream imageStream = getActivity().getContentResolver().openInputStream(imageUri);
                    final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                    imageFood.setImageBitmap(selectedImage);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    if (getActivity() != null) {
                        myToast.setText(getString(R.string.failure));
                        myToast.show();
                    }
                }

            }
        }
    }


    /**
     * To change the image based with 3 different ways:
     *  - Camera
     *  - Gallery
     *  - remove image
     */
    private void changeImage() {
        android.support.v7.widget.PopupMenu popup = new android.support.v7.widget.PopupMenu(getContext(), change_im);
        popup.getMenuInflater().inflate(
                R.menu.popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            // implement click listener.
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.camera:
                        // create Intent with photoFile
                        dispatchTakePictureIntent();
                        return true;
                    case R.id.gallery:
                        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                        photoPickerIntent.setType("image/*");
                        startActivityForResult(photoPickerIntent, RESULT_LOAD_IMG);
                        return true;
                    case R.id.removeImage:
                        removeProfileImage();
                        return true;

                    default:
                        return false;
                }
            }
        });
        popup.show();
    }


    /**
     * Creates Intent with photoFile
     */
    private void dispatchTakePictureIntent() {
        Uri photoURI;
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File

            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(getContext(),
                        "com.example.android.fileproviderR",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }


    /**
     * Method to create image file with ExternalFilesDir
     * @return
     * @throws IOException
     */
    private File createImageFile() throws IOException {
        // Create an image file name
        //String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + "profile";
        File storageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }


    /**
     * Method to remove the profile image
     */
    private void removeProfileImage() {
        imageFood.setImageResource(R.drawable.plate_fork);
    }


    /**
     * Method to set the profile picture
     * @param currentPhotoPath
     */
    private void setPic(String currentPhotoPath) {
        // Get the dimensions of the View
        int targetW = imageFood.getWidth();
        int targetH = imageFood.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);

        if (bitmap != null) {

            try {
                bitmap = rotateImageIfRequired(bitmap, currentPhotoPath);
            } catch (IOException e) {
                e.printStackTrace();
            }

            imageFood.setImageBitmap(bitmap);
        }
    }


    /**
     * To rotate the image if required
     * @param img
     * @param currentPhotoPath
     * @return
     * @throws IOException
     */
    private static Bitmap rotateImageIfRequired(Bitmap img, String currentPhotoPath) throws IOException {

        ExifInterface ei = new ExifInterface(currentPhotoPath);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }


    /**
     * It rotates the image by the given degrees
     * @param img
     * @param degree
     * @return
     */
    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }


    @Override
    public void onStop() {
        super.onStop();

        navigation.setVisibility(View.VISIBLE);

        foodReference.removeAllListener();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        foodReference.removeAllListener();
    }


}