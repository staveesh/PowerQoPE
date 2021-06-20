package za.ac.uct.cs.powerqope;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import za.ac.uct.cs.powerqope.MeasurementScheduler.SchedulerBinder;

public class MainActivity extends AppCompatActivity {

    TextView statusBar, statsBar;

    private static MainActivity app;
    private MeasurementScheduler scheduler;
    private BroadcastReceiver receiver;
    private boolean isBound = false;
    private boolean isBindingToService = false;
    public static final int PERMISSIONS_REQUEST_CODE = 6789;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d("onServiceConnected called");
            // We've bound to LocalService, cast the IBinder and get LocalService
            // instance
            SchedulerBinder binder = (SchedulerBinder) service;
            scheduler = binder.getService();
            isBound = true;
            isBindingToService = false;
            initializeStatusBar();
            MainActivity.this.sendBroadcast(new UpdateIntent("",
                    UpdateIntent.SCHEDULER_CONNECTED_ACTION));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Logger.d("onServiceDisconnected called");
            isBound = false;
        }
    };

    public MeasurementScheduler getScheduler() {
        if (isBound) {
            return this.scheduler;
        } else {
            bindToService();
            return null;
        }
    }

    private void bindToService() {
        if (!isBindingToService && !isBound) {
            // Bind to the scheduler service if it is not bounded
            Intent intent = new Intent(this, MeasurementScheduler.class);
            bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
            isBindingToService = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = this;
        setContentView(R.layout.activity_main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnNavigationItemSelectedListener(navListener);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new ConfigureFragment()).commit();

        statusBar = findViewById(R.id.systemStatusBar);
        statsBar = findViewById(R.id.systemStatsBar);

        Intent intent = new Intent(this, MeasurementScheduler.class);
        this.startService(intent);

        this.receiver = new BroadcastReceiver() {
            @Override
            // All onXyz() callbacks are single threaded
            public void onReceive(Context context, Intent intent) {
                // Update the status bar on SYSTEM_STATUS_UPDATE_ACTION intents
                String statusMsg = intent.getStringExtra(UpdateIntent.STATUS_MSG_PAYLOAD);
                if (statusMsg != null) {
                    Log.d("Broadcast information", statusMsg);
                    updateStatusBar(statusMsg);
                } else if (scheduler != null) {
                    initializeStatusBar();
                }

                String statsMsg = intent.getStringExtra(UpdateIntent.STATS_MSG_PAYLOAD);
                if (statsMsg != null) {
                    updateStatsBar(statsMsg);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
        this.registerReceiver(this.receiver, filter);
    }

    @Override
    protected void onStart() {
        bindToService();
        requestPermissions();
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(serviceConn);
            isBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        Logger.d("onDestroy called");
        super.onDestroy();
        this.unregisterReceiver(this.receiver);
    }

    private final BottomNavigationView.OnNavigationItemSelectedListener navListener = item -> {
        Fragment selectedFragment = null;
        switch (item.getItemId()){
            case R.id.configure:
                selectedFragment = new ConfigureFragment();
                break;
            case R.id.measure:
                selectedFragment = new MeasurementsFragment();
                break;
            case R.id.results:
                selectedFragment = new ResultsFragment();
                break;
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, selectedFragment).commit();
        return true;
    };

    public static MainActivity getCurrentApp() {
        return app;
    }

    private void requestPermissions() {
        if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION))
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.READ_PHONE_STATE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Please grant the following permissions");
                builder.setMessage("Read phone state, Access location");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                },
                                PERMISSIONS_REQUEST_CODE
                        );
                    }
                });
                builder.setNegativeButton("Cancel", null);
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            } else{
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        PERMISSIONS_REQUEST_CODE
                );
            }
        }
    }

    private void initializeStatusBar() {
        if (this.scheduler.isPauseRequested()) {
            updateStatusBar(MainActivity.this.getString(R.string.pauseMessage));
        } else if (!scheduler.hasBatteryToScheduleExperiment()) {
            updateStatusBar(MainActivity.this.getString(R.string.powerThreasholdReachedMsg));
        } else {
            MeasurementTask currentTask = scheduler.getCurrentTask();
            if (currentTask != null) {
                if (currentTask.getDescription().priority == MeasurementTask.USER_PRIORITY) {
                    updateStatusBar("User task " + currentTask.getDescriptor() + " is running");
                } else {
                    updateStatusBar("System task " + currentTask.getDescriptor() + " is running");
                }
            } else {
                updateStatusBar(MainActivity.this.getString(R.string.resumeMessage));
            }
        }
    }

    private void updateStatusBar(String statusMsg) {
        if (statusMsg != null) {
            statusBar.setText(statusMsg);
        }
    }

    private void updateStatsBar(String statsMsg) {
        if (statsMsg != null) {
            statsBar.setText(statsMsg);
        }
    }

}