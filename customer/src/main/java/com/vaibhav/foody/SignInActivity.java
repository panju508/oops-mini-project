package com.vaibhav.foody;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


import java.util.HashMap;

/**
 * This is the first activity launched by the navigator. It is the activity for the Rider sign in.
 * There are 2 ways for the sign in:
 *  - Google sign in via OAuth
 *  - Sign in with email and password
 */
public class SignInActivity extends AppCompatActivity {

    private Toast myToast;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private boolean alreadyAccessed;

    private EditText edPassword, edEmail;
    private ImageButton cancPassword, cancEmail;
    private Button signInButton, signUpButton;
    private SignInButton googleButton;
    private ConnectionManager connectionManager;
    //this is the layout that must be hide as default: it contains the mail and password fields
    private ConstraintLayout login_constraint;
    private ProgressBar progress_bar;

    private HashMap<String, MyDatabaseReference> dbReferenceList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.signin_layout);
        connectionManager = new ConnectionManager();
        if (!connectionManager.haveNetworkConnection(this))
            connectionManager.showDialog(this);

        myToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mAuth = FirebaseAuth.getInstance();

        /** Configure sign-in to request the user's ID, email address, and basic
         * profile. ID and basic profile are included in DEFAULT_SIGN_IN. */
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
//                  new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//                .requestEmail()
//                //.requestIdToken("504775808769-jbk1ab9gqb7gsi149mmvqhre1v37ji2k.apps.googleusercontent.com")
//                //.requestServerAuthCode("504775808769-jbk1ab9gqb7gsi149mmvqhre1v37ji2k.apps.googleusercontent.com", false)
//                .build();

        /** Build a GoogleSignInClient with the options specified by gso. */
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        alreadyAccessed= false; // flag to check if the login is already performed with google o normally
                                // to avoid the other one


        //search for the views
        edPassword = (EditText) findViewById(R.id.edPassword);
        cancPassword = (ImageButton) findViewById(R.id.cancel_password);
        edEmail = (EditText) findViewById(R.id.edEmail);
        cancEmail = (ImageButton) findViewById(R.id.cancel_email);

        googleButton = (SignInButton) findViewById(R.id.google_button);
        googleButton.setSize(SignInButton.SIZE_STANDARD);

        signInButton = (Button) findViewById(R.id.ButtonSignIn);
        signUpButton = (Button) findViewById(R.id.ButtonSignUp);

        //set the listener
        googleButton.setOnClickListener(signInRoutine);
        signInButton.setOnClickListener(signInRoutine);
        signUpButton.setOnClickListener(signInRoutine);

        login_constraint = (ConstraintLayout) findViewById(R.id.login_constraint);
        progress_bar = (ProgressBar) findViewById(R.id.progressBar);
        //default: show the progressBar only
        show_progress();

        dbReferenceList = new HashMap<>();
    }
    /**
     * To show the progress bar
     */
    private void show_progress() {
        login_constraint.setVisibility(View.GONE);
        progress_bar.setVisibility(View.VISIBLE);
    }
    /**
     * To hide the progress bar and show the login form
     */
    private void show_login_form() {
        progress_bar.setVisibility(View.GONE);
        login_constraint.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        handleButton();
        buttonListener();
    }


    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            final DatabaseReference reference = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
            dbReferenceList.put("user", new MyDatabaseReference(reference));

            dbReferenceList.get("user").setSingleValueListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists())
                        if (dataSnapshot.getValue().toString().equals("customer")){
                            if (!alreadyAccessed) {
                                alreadyAccessed = true;
                                access();
                            }
                        }
                        else {
                            show_login_form();
                            FirebaseAuth.getInstance().signOut();
                        }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("Valerio", "SignIn customer -> onStart -> onCancelled: " + databaseError.getMessage());
                }
            });
        } else
            show_login_form();

        //check if signed in with Google
        final GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            if(!alreadyAccessed) {
                firebaseAuthWithGoogle(account);
            }
        }
    }


    /**
     * Method to access the app
     */    public void access() {
        Intent myIntent = new Intent(SignInActivity.this, NavigatorActivity.class);
        SignInActivity.this.startActivity(myIntent);
        finish();
    }

    /**
     * Method to verify the sign in through email and password
     * @param email
     * @param password
     */
    public void signIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d("matte", "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            DatabaseReference reference = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());

                            dbReferenceList.put("userSignIn", new MyDatabaseReference(reference));
                            dbReferenceList.get("userSignIn").setSingleValueListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    if (dataSnapshot.exists())
                                        if (dataSnapshot.getValue().toString().equals("customer")) {
                                            myToast.setText(getString(R.string.login_succ));
                                            myToast.show();
                                            access();
                                        } else {
                                            myToast.setText(getString(R.string.login_fail));
                                            myToast.show();
                                        }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                    Log.d("Valerio", "SignIn customer -> onStart -> onCancelled: " + databaseError.getMessage());
                                }
                            });
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.d("matte", "signInWithEmail:failure", task.getException());
                            myToast.setText(getString(R.string.login_fail));
                            myToast.show();
                        }

                        // ...
                    }
                });
    }

    private boolean implemented = true;
    //request code for the Google sign in activity
    private int RC_SIGN_IN = 0;
    /**
     * The listener for the sign in button. It handles both the Google sign in and the email and password sign in
     */
    private View.OnClickListener signInRoutine = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {

                case R.id.google_button:
                    if (implemented) {
                        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                        startActivityForResult(signInIntent, RC_SIGN_IN);
                    } else {
                        myToast.setText("This feature will be supported in the next version :) ");
                        myToast.show();
                    }

                    break;

                case R.id.ButtonSignIn:
                    if (!edEmail.getText().toString().isEmpty() && !edPassword.getText().toString().isEmpty())
                        signIn(edEmail.getText().toString(), edPassword.getText().toString());
                    else {
                        myToast.setText(getString(R.string.void_fields));
                        myToast.show();
                    }

                    break;

                case R.id.ButtonSignUp:
                    Intent myIntent = new Intent(SignInActivity.this, SignUpActivity.class);
                    SignInActivity.this.startActivity(myIntent);
                    break;


            }
        }
    };

    /**
     * It handles the result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            // Signed in successfully, show authenticated UI.
            firebaseAuthWithGoogle(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.d("matte", "signInResult:failed code=" + e.getStatusCode());
        }
    }
    /**
     * To authenticate with firebase using the Google OAuth protocol
     * @param account
     */
    private void firebaseAuthWithGoogle(final GoogleSignInAccount account) {
        Log.d("matte", "firebaseAuthWithGoogle:" + account.getId());

        String s = account.getIdToken();

        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d("matte", "signInWithCredential:success");

                            final DatabaseReference reference = FirebaseDatabase.getInstance()
                                    .getReference("users").child(mAuth.getCurrentUser().getUid());
                            dbReferenceList.put("user", new MyDatabaseReference(reference));

                            dbReferenceList.get("user").setSingleValueListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    if (dataSnapshot.exists()) {
                                        if (!dataSnapshot.getValue().toString().equals("customer")){
                                            myToast.setText(getApplicationContext().getString(R.string.already_used));
                                            myToast.show();
                                            FirebaseAuth.getInstance().signOut();
                                            mGoogleSignInClient.revokeAccess();
                                            show_login_form();
                                        }
                                        else{
                                            if(!alreadyAccessed) {
                                                alreadyAccessed = true;
                                                access();
                                            }
                                        }
                                    }
                                    else {
                                        Intent myIntent = new Intent(SignInActivity.this,
                                                SignUpGoogleActivity.class);
                                        SignInActivity.this.startActivity(myIntent);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                    Log.d("Valerio", "SignIn customer -> onStart -> onCancelled: " + databaseError.getMessage());
                                }
                            });
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.d("matte", "signInWithCredential:failure", task.getException());
                            Snackbar.make(findViewById(R.id.signin_main_layout), "Authentication Failed.", Snackbar.LENGTH_SHORT).show();
                        }
                    }//onComplete end
                });
    }

    public void clearText(View view) {
        if (view.getId() == R.id.cancel_email)
            edEmail.setText("");
        else if (view.getId() == R.id.cancel_password)
            edPassword.setText("");
    }


    public void handleButton() {
        cancEmail.setVisibility(View.INVISIBLE);
        cancPassword.setVisibility(View.INVISIBLE);

        edEmail.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus)
                    showButton(edEmail, cancEmail);
                else
                    hideButton(cancEmail);
            }
        });

        edEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showButton(edEmail, cancEmail);
            }
        });

        edPassword.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus)
                    showButton(edPassword, cancPassword);
                else
                    hideButton(cancPassword);
            }
        });

        edPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showButton(edPassword, cancPassword);
            }
        });
    }

    public void buttonListener() {
        edEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (edEmail.isFocused())
                    showButton(edEmail, cancEmail);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (edEmail.isFocused())
                    showButton(edEmail, cancEmail);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (edEmail.isFocused())
                    showButton(edEmail, cancEmail);
            }
        });

        edPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (edPassword.isFocused())
                    showButton(edPassword, cancPassword);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (edPassword.isFocused())
                    showButton(edPassword, cancPassword);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (edPassword.isFocused())
                    showButton(edPassword, cancPassword);
            }
        });
    }

    public void showButton(EditText field, ImageButton button) {
        if (field.getText().toString().length() > 0)
            button.setVisibility(View.VISIBLE);
        else
            button.setVisibility(View.INVISIBLE);
    }

    public void hideButton(ImageButton button) {
        button.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onStop() {
        super.onStop();
        for (MyDatabaseReference my_ref : dbReferenceList.values())
            my_ref.removeAllListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (MyDatabaseReference my_ref : dbReferenceList.values())
            my_ref.removeAllListener();
    }
}
