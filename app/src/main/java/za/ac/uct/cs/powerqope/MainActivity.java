package za.ac.uct.cs.powerqope;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Properties;

import za.ac.uct.cs.powerqope.MeasurementScheduler.SchedulerBinder;
import za.ac.uct.cs.powerqope.dns.ConfigurationAccess;
import za.ac.uct.cs.powerqope.dns.DNSFilterService;
import za.ac.uct.cs.powerqope.util.Util;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    TextView statusBar, statsBar;

    private static MainActivity app;
    private MeasurementScheduler scheduler;
    private BroadcastReceiver receiver;
    private boolean isBound = false;
    private boolean isBindingToService = false;
    public static final int PERMISSIONS_REQUEST_CODE = 6789;
    private String target = null;
    private static String WORKDIR;
    protected static ConfigurationAccess CONFIG = ConfigurationAccess.getLocal();
    protected static Properties config = null;
    protected static boolean switchingConfig = false;

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

    public static void reloadLocalConfig() {
        if (app != null && CONFIG.isLocal())
            app.loadAndApplyConfig(false);
    }

    protected void loadAndApplyConfig(boolean startApp) {

        config = getConfig();

        if (config != null) {

            if (startApp)
                startup();

        } else
            switchingConfig =false;
    }

    protected void startup() {

        if (DNSFilterService.SERVICE != null) {
            Log.i(TAG, "DNS filter service is running!");
            Log.i(TAG, "Filter statistic since last restart:");
            return;
        }

        try {
            boolean vpnInAdditionToProxyMode = Boolean.parseBoolean(getConfig().getProperty("vpnInAdditionToProxyMode", "false"));
            boolean vpnDisabled = !vpnInAdditionToProxyMode && Boolean.parseBoolean(getConfig().getProperty("dnsProxyOnAndroid", "false"));
            Intent intent = null;
            if (!vpnDisabled)
                intent = VpnService.prepare(this.getApplicationContext());
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else { //already prepared or VPN disabled
                startDNSSvc();
            }
        } catch (NullPointerException e) { // NullPointer might occur on Android 4.4 when VPN already initialized
            Log.i(TAG, "Seems we are on Android 4.4 or older!");
            startDNSSvc(); // assume it is ok!
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

    }

    private void startDNSSvc() {
        startService(new Intent(this, DNSFilterService.class));
    }

    protected Properties getConfig() {
        try {
            return CONFIG.getConfig();
        } catch (Exception e){
            Log.e(TAG, e.toString());
            return null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            startDNSSvc();
        } else if (requestCode == 0 && resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "VPN dialog not accepted!\r\nPress restart to display dialog again!");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = this;
        setContentView(R.layout.activity_main);
        AndroidEnvironment.initEnvironment(this);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnNavigationItemSelectedListener(navListener);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new ConfigureFragment()).commit();

        statusBar = findViewById(R.id.systemStatusBar);
        statsBar = findViewById(R.id.systemStatsBar);

        if(target == null) {
            target = Util.getWebSocketTarget();
            SharedPreferences prefs = getSharedPreferences(Config.PREF_KEY_RESOLVED_TARGET, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Config.PREF_KEY_RESOLVED_TARGET, target);
            editor.apply();
            WebSocketConnector webSocketConnector = WebSocketConnector.getInstance();
            WebSocketConnector.setContext(getBaseContext());
            WebSocketConnector.setScheduler(getScheduler());
            if(!webSocketConnector.isConnected())
                webSocketConnector.connectWebSocket(target);
        }

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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length >0 ){
            boolean allPermissionsGranted = true;
            for(int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                    allPermissionsGranted = false;
            }
            if(allPermissionsGranted)
                loadAndApplyConfig(true);
        }
        else {
            if (grantResults.length == 0)
                Log.e(TAG, "grantResults is empty - Assuming permission denied!");
            System.exit(-1);
        }
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
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.READ_PHONE_STATE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Please grant the following permissions");
                builder.setMessage("Read phone state, Access location, Access Storage");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
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
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
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