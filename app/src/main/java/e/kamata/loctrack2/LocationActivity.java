package e.kamata.loctrack2;

//ver0.1-計測開始ボタンを押すと、位置情報をテキストログとして出力する。
//位置情報の更新頻度は最遅で1分(60000msec)

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocationActivity extends AppCompatActivity {

    private static final String TAG = "LocationActivity";

    // Fused Location Provider API.
    private FusedLocationProviderClient fusedLocationClient;

    // Location Settings APIs.
    private SettingsClient settingsClient;
    private static LocationSettingsRequest locationSettingsRequest;
    private static LocationCallback locationCallback;
    private static LocationRequest locationRequest;
    public Location location;
    public static double[] fusedData = new double[6];
    private String lastUpdateTime;
    private static Boolean requestingLocationUpdates;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private int priority = 0;
    private ScrollView scrollView;
    private TextView textView;
    private TextView longitude;
    private TextView latitude;
    private static String textLog;
    String JSON;
    //HACK add state message
    private static final DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.JAPAN);
//    ComponentName mServicename;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        final Button buttonStart = findViewById(R.id.button_start);
        final Button buttonStop = findViewById(R.id.button_stop);
        final Button buttonSend = findViewById(R.id.button_sendToServer);
        buttonStop.setEnabled(false);
        buttonSend.setEnabled(false);
        scrollView = findViewById(R.id.locationView);
        textView = findViewById(R.id.text_view);
        longitude = findViewById(R.id.longitude);
        latitude = findViewById(R.id.latitude);
        textLog = "press start...\n";
        textView.setText(textLog);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        priority = 0;

        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();

        // 測位開始ボタン; START
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplication(),LocationService.class);
                if(Build.VERSION.SDK_INT >= 26){
                    startForegroundService(intent);
                }
                Log.d("Button","Location track Started.");
                MyJobService.schedule(getApplicationContext());
                startLocationUpdates();

                buttonStart.setEnabled(false);
                buttonStop.setEnabled(true);
                buttonSend.setEnabled(true);
            }
        });

        // 測位終了ボタン; STOP
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLocationUpdates();
                MyJobService.cancelJobs(getApplicationContext());

                buttonStart.setEnabled(true);
                buttonStop.setEnabled(false);
            }
        });

        // データベースにデータ(Json形式)を投げるボタン; SEND TO SERVER
        buttonSend.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                startPostToServer();

                buttonStop.setEnabled(true);
                buttonSend.setEnabled(true);
            }

//
//            public void onLocationUpdate(View v){
//                buttonSend.setEnabled(true);
//            }
        });

    }

    // locationのコールバックを受け取る
    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                location = locationResult.getLastLocation();
                lastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                updateLocationUI();
            }
        };
    }

    //Location情報の更新があるとログを更新する
    private void updateLocationUI() {
        // getLastLocation()からの情報がある場合のみ
        if (location != null) {
//            buttonSend.onLocationUodate;
            String fusedName[] = {
                    "Latitude", "Longitude", "Accuracy",
                    "Altitude", "Speed", "Bearing"
            };

            fusedData[0] = location.getLatitude();
            fusedData[1] = location.getLongitude();
            fusedData[2] = location.getAccuracy();
            fusedData[3] = location.getAltitude();
            fusedData[4] = location.getSpeed();
            fusedData[5] = location.getBearing();

            String sendData[] = {
                    String.valueOf(fusedData[0]), //Latitude
                    String.valueOf(fusedData[1])  //Longitude
            };

            StringBuilder strBuf =
                    new StringBuilder("---------- UpdateLocation ---------- \n");

            for(int i=0; i<fusedName.length; i++) {
                strBuf.append(fusedName[i]);
                strBuf.append(" = ");
                strBuf.append(String.valueOf(fusedData[i]));
                strBuf.append("\n");
            }

            strBuf.append("Time");
            strBuf.append(" = ");
            strBuf.append(lastUpdateTime);
            strBuf.append("\n");

            textLog += strBuf;
            textView.setText(textLog);
            longitude.setText(String.valueOf(fusedData[0]));
            latitude.setText(String.valueOf(fusedData[1]));
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
            Log.i(TAG,"Location Update: "+String.valueOf(fusedData[0])
                    +","+String.valueOf(fusedData[1])+" "+df.format(System.currentTimeMillis()));

            //JSON形式で位置情報を送るための処理
            try {
                stringifyToJson(sendData);
            } catch (Exception e){
                Toast toast = Toast.makeText(this,
                        "Exception:Json文字列に変換できませんでした", Toast.LENGTH_SHORT);
                toast.show();
            }

        }

    }

    //特定の精度でリクエストを投げる
    private void createLocationRequest() {
        locationRequest = LocationRequest.create();

        if (priority == 0) {
            // 高い精度の位置情報を取得したい場合
            // インターバルを例えば5000msecに設定すれば
            // マップアプリのようなリアルタイム測位となる
            // 主に精度重視のためGPSが優先的に使われる
            locationRequest.setPriority(
                    LocationRequest.PRIORITY_HIGH_ACCURACY);

        } else if (priority == 1) {
            // バッテリー消費を抑えたい場合、精度は100mと悪くなる
            // 主にwifi,電話網での位置情報が主となる
            // この設定の例としては　setInterval(1時間)、setFastestInterval(1分)
            locationRequest.setPriority(
                    LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        } else if (priority == 2) {
            // バッテリー消費を抑えたい場合、精度は10kmと悪くなる
            locationRequest.setPriority(
                    LocationRequest.PRIORITY_LOW_POWER);

        } else {
            // 受け身的な位置情報取得でアプリが自ら測位せず、
            // 他のアプリで得られた位置情報は入手できる
            locationRequest.setPriority(
                    LocationRequest.PRIORITY_NO_POWER);
        }

        // アップデートのインターバル期間設定
        // このインターバルは測位データがない場合はアップデートしません
        // また状況によってはこの時間よりも長くなることもあり
        // 必ずしも正確な時間ではありません
        // 他に同様のアプリが短いインターバルでアップデートしていると
        // それに影響されインターバルが短くなることがあります。
        // 単位：msec
        locationRequest.setInterval(60000);
        // このインターバル時間は正確です。これより早いアップデートはしません。
        // 単位：msec
        locationRequest.setFastestInterval(5000);

    }

    // 端末で測位できる状態か確認する。wifi, GPSなどがOffになっているとエラー情報のダイアログが出る
    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder =
                new LocationSettingsRequest.Builder();

        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i("debug", "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i("debug", "User chose not to make required location settings changes.");
                        requestingLocationUpdates = false;
                        break;
                }
                break;
        }
    }

    // FusedLocationApiによるlocation updatesをリクエスト
    void startLocationUpdates() {
        textLog = "location track start\n";
        textView.setText(textLog);
        // Begin by checking if the device has the necessary location settings.
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(this,
                        new OnSuccessListener<LocationSettingsResponse>() {
                            @Override
                            public void onSuccess(
                                    LocationSettingsResponse locationSettingsResponse) {
                                Log.i("debug", "All location settings are satisfied.");

                                // パーミッションの確認
                                if (ActivityCompat.checkSelfPermission(
                                        LocationActivity.this,
                                        Manifest.permission.ACCESS_FINE_LOCATION) !=
                                        PackageManager.PERMISSION_GRANTED
                                        && ActivityCompat.checkSelfPermission(
                                        LocationActivity.this,
                                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                                        PackageManager.PERMISSION_GRANTED) {

                                    // todo: Consider calling
                                    //    ActivityCompat#requestPermissions
                                    // here to request the missing permissions, and then overriding
                                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                    //                                          int[] grantResults)
                                    // to handle the case where the user grants the permission. See the documentation
                                    // for ActivityCompat#requestPermissions for more details.
                                    return;
                                }
                                fusedLocationClient.requestLocationUpdates(
                                        locationRequest, locationCallback, Looper.myLooper());

                            }
                        })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i("debug", "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(
                                            LocationActivity.this,
                                            REQUEST_CHECK_SETTINGS);

                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i("debug", "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e("debug", errorMessage);
                                Toast.makeText(LocationActivity.this,
                                        errorMessage, Toast.LENGTH_LONG).show();

                                requestingLocationUpdates = false;
                        }

                    }
                });

        requestingLocationUpdates = true;
    }

    public void stopLocationUpdates() {
        textLog += "location track stop\n";
        textView.setText(textLog);
        Log.i("test","stopLocationUpdates");

        if (!requestingLocationUpdates) {
            Log.d("debug", "stopLocationUpdates: " +
                    "updates never requested, no-op.");
            return;
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener(this,
                        new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                requestingLocationUpdates = false;
                            }
                        });
    }

    //位置情報をサーバーに投げる
    public void startPostToServer(){
        WriteActivity writeActivity = new WriteActivity();
            textLog += "--------------------------------------------------------\n" +
                    "位置情報を送信しています...\n";
            textView.setText(textLog);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });

        //位置情報取得を一時停止
//        stopLocationUpdates();
        writeActivity.startVolley(fusedData[0],fusedData[1]);

        textLog += "--------------------------------------------------------\n" +
                "送信が完了しました\n";
        textView.setText(textLog);
//        Toast.makeText(this, "通信が完了しました", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // バッテリー消費を鑑みLocation requestを止める
        Log.i(TAG,"onPause at "+df.format(System.currentTimeMillis()));
//        Log.i("LocationActivity","LocationRequest has stopped considering consuming battery.");
//        stopLocationUpdates();
    }

    @Override
    protected void onResume(){
        super.onResume();
        //復帰時に位置情報を送信した時間をユーザーに報告する
        Log.i(TAG,"onResume at "+df.format(System.currentTimeMillis()));
        printSendLogs();
    }

    //Jacksonを用いてJSON文字列に変換する
    public void stringifyToJson(String[] args) throws JsonProcessingException{
        ObjectMapper mapper = new ObjectMapper();
        JSON = mapper.writeValueAsString(args);
    }

    //スリープから復帰したらその間に位置情報を送信した履歴を出力する
    public void printSendLogs(){
        MyJobService job = new MyJobService();
        ArrayList<ArrayList> locLog = job.getSendLogs();

        Log.i(TAG,"locsendlog:"+locLog);

        if(locLog!=null) {
            StringBuilder strBuf = new StringBuilder("------------位置情報送信--------------\n");
            strBuf.append("id: (latitude,longitude) at time\n");
            for (int i = 0; i < locLog.size(); i++) {
                double latitude = (double) locLog.get(i).get(0);
                double longitude = (double) locLog.get(i).get(1);
                Long time = (Long) locLog.get(i).get(2);
                strBuf.append(i + ": (" + latitude + " , " + longitude + ") at " + time + "\n");
                textLog += strBuf;
                textView.setText(textLog);
                scrollView.post(new Runnable() {
                    @Override
                    public void run() { scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
            job.clearSendLogs();
        }
    }

    public double[] getLocation(){
        return fusedData;
    }

}