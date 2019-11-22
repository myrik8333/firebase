package com.example.firebase;

import android.content.Intent;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import mjson.Json;


public class CustomPrintActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private static final int REQUEST_SINGIN = 1;
    private TextView txt;
    public static final String TAG = "mysupertag";
    public static final String URLBASE = "https://www.google.com/cloudprint/";
    String printer_id;
    String PDFPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);
        txt = (TextView) findViewById(R.id.txt);
        mAuth = FirebaseAuth.getInstance();
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.gg_client_web_id))
                .requestEmail()
                .requestServerAuthCode(getString(R.string.gg_client_web_id))
                .requestScopes(new Scope("https://www.googleapis.com/auth/cloudprint"))
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }


        });


        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // залогинился
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    // разлоинился
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
            }
        };

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "error connecting: " + connectionResult.getErrorMessage());
        Toast.makeText(this, "error CONN", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == REQUEST_SINGIN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            Log.d(TAG,"result: "+result.toString());
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
                Toast.makeText(this, "error: Google Sign In failed: " + result.getStatus(), Toast.LENGTH_LONG).show();
                Log.d(TAG,"result: "+result.getStatus());
            }
        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, REQUEST_SINGIN);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    private void firebaseAuthWithGoogle(final GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
                        FirebaseUser user = task.getResult().getUser();
                        txt.setText(user.getDisplayName() + "\n" + user.getEmail());//todo
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(CustomPrintActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                        getAccess(acct.getServerAuthCode());
                    }
                });
    }

    private void getPrinters(final String token) {
        Log.d(TAG, "TOKEN: " + token);
        String url = URLBASE + "search";
        Ion.with(this)
                .load("GET", url)
                .addHeader("Authorization", "Bearer " + token)
                .asString()
                .withResponse()
                .setCallback(new FutureCallback<Response<String>>() {
                    @Override
                    public void onCompleted(Exception e, Response<String> result) {
                        JSONObject json;
                        String printers;
                        String[] printer_info;

                        Log.d(TAG, "finished " + result.getHeaders().code() + ": " +
                                result.getResult());
                        //получаем айди принтера  и в лог его
                        if (e == null) {
                            try {
                                json = new JSONObject(result.getResult());
                                printers = json.getString("printers");
                                printer_info = printers.split("\\,");
                                printer_id = printer_info[21];
                                for (int i = 0 ; i<printer_info.length ; i++){

                                    Log.d("print_info","Pos " + i + ":" + printer_info[i]);
                                }
                                printer_id = printer_id.substring(6,printer_id.length()-1);
                                Log.d("print_id", printer_id);
                                txt.setText(txt.getText()+"\nPrinter_id: " + printer_id);

                               // путь к пдфке
                               PDFPath = "test.png";
                                //печать
                               printPdf(PDFPath,printer_id,token);

                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }
                        }
                        if (e == null) {
                            Log.d(TAG, "nice");
                        } else {
                            Log.d(TAG, "error");
                        }
                    }
                });
    }
    //получаем принтеры
    private void getAccess(String code) {
        String url = "https://www.googleapis.com/oauth2/v4/token";
        Ion.with(this)
                .load("POST", url)
                .setBodyParameter("client_id", getString(R.string.gg_client_web_id))
                .setBodyParameter("client_secret", getString(R.string.gg_client_web_secret))
                .setBodyParameter("code", code)
                .setBodyParameter("grant_type", "authorization_code")
                .asString()
                .withResponse()
                .setCallback(new FutureCallback<Response<String>>() {
                    @Override
                    public void onCompleted(Exception e, Response<String> result) {
                        Log.d(TAG, "result of getAccess: " + result.getResult());
                        if (e == null) {
                            try {
                                JSONObject json = new JSONObject(result.getResult());
                                getPrinters(json.getString("access_token"));
                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }
                        } else {
                            Log.d(TAG, "error");
                        }
                    }
                });
    }
//печать
    private void printPdf(String pdfPath, String printerId, String token) {
        String url = URLBASE + "submit";
        Ion.with(this)
                .load("POST", url)
                .addHeader("Authorization", "Bearer " + token)
                .setMultipartParameter("printerid", printerId)
                .setMultipartParameter("title", "print test")
                .setMultipartParameter("ticket", getTicket())
                .setMultipartFile("content", "application/png", new File(pdfPath))
                .asString()
                .withResponse()
                .setCallback(new FutureCallback<Response<String>>() {
                    @Override
                    public void onCompleted(Exception e, Response<String> result) {
                        if (e == null) {
                            Log.d(TAG, "PRINT CODE: " + result.getHeaders().code() +
                                    ", RESPONSE: " + result.getResult());
                            Json j = Json.read(result.getResult());
                            if (j.at("success").asBoolean()) {
                                Toast.makeText(CustomPrintActivity.this, "Success", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(CustomPrintActivity.this, "ERROR SUCCESS", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(CustomPrintActivity.this, "ERROR NULL", Toast.LENGTH_LONG).show();
                            Log.d(TAG, e.toString());
                        }
                    }
                });
    }

    private String getTicket() {
        Json ticket = Json.object();
        Json print = Json.object();
        ticket.set("version", "1.0");

        print.set("vendor_ticket_item", Json.array());
        print.set("color", Json.object("type", "STANDARD_MONOCHROME"));
        print.set("copies", Json.object("copies", 1));

        ticket.set("print", print);
        return ticket.toString();
    }
}
