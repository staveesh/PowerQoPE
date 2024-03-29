/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package za.ac.uct.cs.powerqope.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Display;
import android.view.WindowManager;
import android.webkit.WebView;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import za.ac.uct.cs.powerqope.Config;
import za.ac.uct.cs.powerqope.DeviceInfo;
import za.ac.uct.cs.powerqope.DeviceProperty;
import za.ac.uct.cs.powerqope.Logger;
import za.ac.uct.cs.powerqope.R;
import za.ac.uct.cs.powerqope.WebSocketConnector;
import za.ac.uct.cs.powerqope.dns.DNSFilterService;


/**
 * Phone related utilities.
 */
public class PhoneUtils {

    private static final String ANDROID_STRING = "Android";
    /**
     * Returned by {@link #getNetwork()}.
     */
    public static final String NETWORK_WIFI = "Wifi";
    /**
     * IP type
     */
    public static final String IP_TYPE_UNKNOWN = "UNKNOWN";
    public static final String IP_TYPE_NONE = "Neither IPv4 nor IPv6";
    public static final String IP_TYPE_IPV4_ONLY = "IPv4 only";
    public static final String IP_TYPE_IPV6_ONLY = "IPv6 only";
    public static final String IP_TYPE_IPV4_IPV6_BOTH = "IPv4 and IPv6";

    /**
     * The app that uses this class. The app must remain alive for longer than
     * PhoneUtils objects are in use.
     *
     * @see #setGlobalContext(Context)
     */
    private static Context globalContext = null;

    /**
     * A singleton instance of PhoneUtils.
     */
    private static PhoneUtils singletonPhoneUtils = null;

    /**
     * Phone context object giving access to various phone parameters.
     */
    private Context context = null;

    /**
     * Allows to obtain the phone's location, to determine the country.
     */
    private LocationManager locationManager = null;

    /**
     * The name of the location provider with "coarse" precision (cell/wifi).
     */
    private String locationProviderName = null;

    /**
     * Allows to disable going to low-power mode where WiFi gets turned off.
     */
    WakeLock wakeLock = null;

    /**
     * Call initNetworkManager() before using this var.
     */
    private ConnectivityManager connectivityManager = null;

    /**
     * Call initNetworkManager() before using this var.
     */
    private TelephonyManager telephonyManager = null;

    /**
     * Tells whether the phone is charging
     */
    private boolean isCharging;
    /**
     * Current battery level in percentage
     */
    private int curBatteryLevel;

    /**
     * Current battery temperature
     */
    private int temperature;

    private BroadcastReceiver powerBroadcastReceiver;
    private BroadcastReceiver networkBroadcastReceiver;

    private int currentSignalStrength = NeighboringCellInfo.UNKNOWN_RSSI;

    /**
     * For monitoring the current network connection type
     **/
    public static int TYPE_WIFI = 1;
    public static int TYPE_MOBILE = 2;
    public static int TYPE_NOT_CONNECTED = 0;
    private int currentNetworkConnection = TYPE_NOT_CONNECTED;

    private DeviceInfo deviceInfo = null;
    /**
     * IP compatibility status
     */
    // Indeterministic type due to client side timer expired
    private int IP_TYPE_CANNOT_DECIDE = 2;
    // Cannot resolve the hostname or cannot reach the destination address
    private int IP_TYPE_UNCONNECTIVITY = 1;
    private int IP_TYPE_CONNECTIVITY = 0;
    /**
     * Domain name resolution status
     */
    private int DN_UNKNOWN = 2;
    private int DN_UNRESOLVABLE = 1;
    private int DN_RESOLVABLE = 0;
    //server configuration port on M-Lab servers
    private int portNum = 6003;
    private int tcpTimeout = 3000;

    @SuppressLint("MissingPermission")
    protected PhoneUtils(Context context) {
        this.context = context;
        powerBroadcastReceiver = new PowerStateChangeReceiver();
        // Registers a receiver for battery change events.
        if (globalContext != null) {
            Intent powerIntent = globalContext.registerReceiver(powerBroadcastReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (powerIntent != null) {
                updateBatteryStat(powerIntent);
            }
        }
        networkBroadcastReceiver = new ConnectivityChangeReceiver();
        // Registers a receiver for network change events.
        globalContext.registerReceiver(networkBroadcastReceiver,
                new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        updateConnectivityInfo(); // does not require the intent to determine connectivity
    }

    /**
     * The owner app class must call this method from its onCreate(), before
     * getPhoneUtils().
     */
    public static synchronized void setGlobalContext(Context newGlobalContext) {
        assert newGlobalContext != null;
        assert singletonPhoneUtils == null;  // Should not yet be created
        // Not supposed to change the owner app
        assert globalContext == null || globalContext == newGlobalContext;

        globalContext = newGlobalContext;
    }

    public static synchronized void releaseGlobalContext() {
        globalContext = null;
        singletonPhoneUtils = null;
    }

    /**
     * Returns the context previously set with {@link #setGlobalContext}.
     */
    public static synchronized Context getGlobalContext() {
        assert globalContext != null;
        return globalContext;
    }

    /**
     * Returns a singleton instance of PhoneUtils. The caller must call
     * {@link #setGlobalContext(Context)} before calling this method.
     */
    public static synchronized PhoneUtils getPhoneUtils() {
        if (singletonPhoneUtils == null) {
            assert globalContext != null;
            singletonPhoneUtils = new PhoneUtils(globalContext);
        }

        return singletonPhoneUtils;
    }

    /**
     * Returns a string representing this phone:
     * <p>
     * "Android_<hardware-type>-<build-release>_<network-type>_" +
     * "<network-carrier>_<mobile-type>_<Portrait-or-Landscape>"
     * <p>
     * hardware-type is e.g. "dream", "passion", "emulator", etc.
     * build-release is the SDK public release number e.g. "2.0.1" for Eclair.
     * network-type is e.g. "Wifi", "Edge", "UMTS", "3G".
     * network-carrier is the mobile carrier name if connected via the SIM card,
     * or the Wi-Fi SSID if connected via the Wi-Fi.
     * mobile-type is the phone's mobile network connection type -- "GSM" or "CDMA".
     * <p>
     * If the device screen is currently in lanscape mode, "_Landscape" is
     * appended at the end.
     * <p>
     * TODO(klm): This needs to be converted into named URL args from positional,
     * both here and in the iPhone app. Otherwise it's hard to add extensions,
     * especially if there is optional stuff like
     *
     * @return a string representing this phone
     */
    public String generatePhoneId() {
        String device = Build.DEVICE.equals("generic") ? "emulator" : Build.DEVICE;
        String network = getNetwork();
        String carrier = (network == NETWORK_WIFI) ?
                getWifiCarrierName() : getTelephonyCarrierName();

        StringBuilder stringBuilder = new StringBuilder(ANDROID_STRING);
        stringBuilder.append('-').append(device).append('_')
                .append(Build.VERSION.RELEASE).append('_').append(network)
                .append('_').append(carrier).append('_').append(getTelephonyPhoneType())
                .append('_').append(isLandscape() ? "Landscape" : "Portrait");

        return stringBuilder.toString();
    }

    /**
     * Lazily initializes the network managers.
     * <p>
     * As a side effect, assigns connectivityManager and telephonyManager.
     */
    private synchronized void initNetwork() {
        if (connectivityManager == null) {
            ConnectivityManager tryConnectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            TelephonyManager tryTelephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

            // Assign to member vars only after all the get calls succeeded,
            // so that either all get assigned, or none get assigned.
            connectivityManager = tryConnectivityManager;
            telephonyManager = tryTelephonyManager;

            // Some interesting info to look at in the logs
            NetworkInfo[] infos = connectivityManager.getAllNetworkInfo();
            for (NetworkInfo networkInfo : infos) {
                Logger.i("Network: " + networkInfo);
            }
            Logger.i("Phone type: " + getTelephonyPhoneType() +
                    ", Carrier: " + getTelephonyCarrierName());
        }
        assert connectivityManager != null;
        assert telephonyManager != null;
    }

    public String getNetworkClass() {
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info == null || !info.isConnected())
            return "-"; // not connected
        if (info.getType() == ConnectivityManager.TYPE_WIFI)
            return "WiFi";
        if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                case TelephonyManager.NETWORK_TYPE_GSM:
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                case TelephonyManager.NETWORK_TYPE_IWLAN:
                case 19:
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:
                    return "5G";
                default:
                    return "?";
            }
        }
        return "?";
    }

    /**
     * This method must be called in the service thread, as the system will create a Looper in
     * the calling thread which will handle the callbacks.
     */
    public void registerSignalStrengthListener() {
        initNetwork();
        telephonyManager.listen(new SignalStrengthChangeListener(),
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    /**
     * Returns the network that the phone is on (e.g. Wifi, Edge, GPRS, etc).
     */
    public String getNetwork() {
        initNetwork();
        NetworkInfo networkInfo =
                connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo != null &&
                networkInfo.getState() == NetworkInfo.State.CONNECTED) {
            return NETWORK_WIFI;
        } else {
            return getTelephonyNetworkType();
        }
    }

    private static final String[] NETWORK_TYPES = {
            "UNKNOWN",  // 0  - NETWORK_TYPE_UNKNOWN
            "GPRS",     // 1  - NETWORK_TYPE_GPRS
            "EDGE",     // 2  - NETWORK_TYPE_EDGE
            "UMTS",     // 3  - NETWORK_TYPE_UMTS
            "CDMA",     // 4  - NETWORK_TYPE_CDMA
            "EVDO_0",   // 5  - NETWORK_TYPE_EVDO_0
            "EVDO_A",   // 6  - NETWORK_TYPE_EVDO_A
            "1xRTT",    // 7  - NETWORK_TYPE_1xRTT
            "HSDPA",    // 8  - NETWORK_TYPE_HSDPA
            "HSUPA",    // 9  - NETWORK_TYPE_HSUPA
            "HSPA",     // 10 - NETWORK_TYPE_HSPA
            "IDEN",     // 11 - NETWORK_TYPE_IDEN
            "EVDO_B",   // 12 - NETWORK_TYPE_EVDO_B
            "LTE",      // 13 - NETWORK_TYPE_LTE
            "EHRPD",    // 14 - NETWORK_TYPE_EHRPD
    };

    /**
     * Returns mobile data network connection type.
     */
    private String getTelephonyNetworkType() {
        assert NETWORK_TYPES[14].compareTo("EHRPD") == 0;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            int networkType = telephonyManager.getNetworkType();
            if (networkType < NETWORK_TYPES.length) {
                // Network type might get changed if getNetwokrType() called twice
                return NETWORK_TYPES[networkType];
            } else {
                return "Unrecognized: " + networkType;
            }
        }
        return null;
    }

    /**
     * Returns "GSM", "CDMA".
     */
    private String getTelephonyPhoneType() {
        switch (telephonyManager.getPhoneType()) {
            case TelephonyManager.PHONE_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.PHONE_TYPE_GSM:
                return "GSM";
            case TelephonyManager.PHONE_TYPE_NONE:
                return "None";
        }
        return "Unknown";
    }

    /**
     * Returns current mobile phone carrier name, or empty if not connected.
     */
    private String getTelephonyCarrierName() {
        return telephonyManager.getNetworkOperatorName();
    }

    /**
     * Returns current Wi-Fi SSID, or null if Wi-Fi is not connected.
     */
    private String getWifiCarrierName() {
        WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            return wifiInfo.getSSID();
        }
        return null;
    }

    /**
     * Returns the information about cell towers in range. Returns null if the information is
     * not available
     * <p>
     * TODO(wenjiezeng): As folklore has it and Wenjie has confirmed, we cannot get cell info from
     * Samsung phones.
     */
    @SuppressLint("MissingPermission")
    public String getCellInfo(boolean cidOnly) {
        initNetwork();
        List<CellInfo> infos = null;
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            infos = telephonyManager.getAllCellInfo();

        StringBuffer buf = new StringBuffer();
        String tempResult = "";
        int cid = 0, lac = 0, rssi = 0;
        if (infos != null && infos.size() > 0) {
            for (CellInfo info : infos) {
                if (info instanceof CellInfoGsm) {
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) info;
                    cid = cellInfoGsm.getCellIdentity().getCid();
                    lac = cellInfoGsm.getCellIdentity().getLac();
                    rssi = cellInfoGsm.getCellSignalStrength().getDbm();
                } else if (info instanceof CellInfoLte) {
                    CellInfoLte cellInfoLte = (CellInfoLte) info;
                    cid = cellInfoLte.getCellIdentity().getCi();
                    lac = cellInfoLte.getCellIdentity().getTac();
                    rssi = cellInfoLte.getCellSignalStrength().getDbm();
                } else if (info instanceof CellInfoWcdma) {
                    CellInfoWcdma cellInfoLte = (CellInfoWcdma) info;
                    cid = cellInfoLte.getCellIdentity().getCid();
                    lac = cellInfoLte.getCellIdentity().getLac();
                    rssi = cellInfoLte.getCellSignalStrength().getDbm();
                } else if (info instanceof CellInfoCdma) {
                    CellInfoCdma cellInfoCdma = (CellInfoCdma) info;
                    cid = cellInfoCdma.getCellIdentity().getBasestationId();
                    lac = cellInfoCdma.getCellIdentity().getNetworkId();
                    rssi = cellInfoCdma.getCellSignalStrength().getDbm();
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (info instanceof CellInfoTdscdma) {
                        CellInfoTdscdma cellInfoTdscdma = (CellInfoTdscdma) info;
                        cid = cellInfoTdscdma.getCellIdentity().getCid();
                        lac = cellInfoTdscdma.getCellIdentity().getLac();
                        rssi = cellInfoTdscdma.getCellSignalStrength().getDbm();
                    }
                }
                tempResult = cidOnly ? cid + ";" : lac + ","
                        + cid + "," + rssi + ";";
                buf.append(tempResult);
            }
            // Removes the trailing semicolon
            buf.deleteCharAt(buf.length() - 1);
            return buf.toString();
        } else {
            return null;
        }
    }

    /**
     * Lazily initializes the location manager.
     * <p>
     * As a side effect, assigns locationManager and locationProviderName.
     */
    @SuppressLint("MissingPermission")
    private synchronized void initLocation() {
        if (locationManager == null) {
            LocationManager manager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            Criteria criteriaFine = new Criteria();
            criteriaFine.setAccuracy(Criteria.ACCURACY_FINE);
            criteriaFine.setPowerRequirement(Criteria.POWER_LOW);
            String providerName =
                    manager.getBestProvider(criteriaFine, /*enabledOnly=*/true);
            Logger.i("Using best location provider : " + providerName);
            List<String> providers = manager.getAllProviders();
            for (String providerNameIter : providers) {
                try {
                    LocationProvider provider = manager.getProvider(providerNameIter);
                } catch (SecurityException se) {
                    // Not allowed to use this provider
                    Logger.w("Unable to use provider " + providerNameIter);
                    continue;
                }
                Logger.i(providerNameIter + ": " +
                        (manager.isProviderEnabled(providerNameIter) ? "enabled" : "disabled"));
            }

            /* Make sure the provider updates its location.
             * Without this, we may get a very old location, even a
             * device powercycle may not update it.
             * {@see android.location.LocationManager.getLastKnownLocation}.
             */

            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(context,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                manager.requestLocationUpdates(providerName,
                        /*minTime=*/60000,
                        /*minDistance=*/1,
                        new LoggingLocationListener(),
                        Looper.getMainLooper());
                locationManager = manager;
                locationProviderName = providerName;
            }
        }
        assert locationManager != null;
        assert locationProviderName != null;
    }

    /**
     * Returns the location of the device.
     *
     * @return the location of the device
     */
    @SuppressLint("MissingPermission")
    public Location getLocation() {
        try {
            initLocation();//we asked for the permissions from here
            Location location = null;

            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                location = locationManager.getLastKnownLocation(locationProviderName);
                Logger.i("Got the location object");
            }
            if (location == null) {
                Logger.e("Cannot obtain location from provider " + locationProviderName);
                return new Location("unknown");
            }
            return location;
        } catch (IllegalArgumentException e) {
            Logger.e("Cannot obtain location", e);
            return new Location("unknown");
        }
    }

    /**
     * Wakes up the CPU of the phone if it is sleeping.
     */
    public synchronized void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, " measurements:mywakelocktag");
        }
        Logger.d("PowerLock acquired");
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);//this was recommended
    }

    /**
     * Release the CPU wake lock. WakeLock is reference counted by default: no need to worry
     * about releasing someone else's wake lock
     */
    public synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                Logger.i("PowerLock released");
            } catch (RuntimeException e) {
                Logger.e("Exception when releasing wakeup lock", e);
            }
        }
    }

    /**
     * Release all resource upon app shutdown
     */
    public synchronized void shutDown() {
        if (this.wakeLock != null) {
            /* Wakelock are ref counted by default. We disable this feature here to ensure that
             * the power lock is released upon shutdown.
             */
            wakeLock.setReferenceCounted(false);
            wakeLock.release();
        }
        context.unregisterReceiver(networkBroadcastReceiver);
        releaseGlobalContext();
    }

    /**
     * Returns true if the phone is in landscape mode.
     */
    public boolean isLandscape() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        return display.getWidth() > display.getHeight();
    }

    /**
     * Captures a screenshot of a WebView, except scrollbars, and returns it as a
     * Bitmap.
     *
     * @param webView The WebView to screenshot.
     * @return A Bitmap with the screenshot.
     */
    public static Bitmap captureScreenshot(WebView webView) {
        Picture picture = webView.capturePicture();
        int width = Math.min(picture.getWidth(),
                webView.getWidth() - webView.getVerticalScrollbarWidth());
        int height = Math.min(picture.getHeight(), webView.getHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(bitmap);
        cv.drawPicture(picture);
        return bitmap;
    }

    /**
     * A dummy listener that just logs callbacks.
     */
    private static class LoggingLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            Logger.d("location changed");
        }

        @Override
        public void onProviderDisabled(String provider) {
            Logger.d("provider disabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Logger.d("provider enabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Logger.d("status changed: " + provider + "=" + status);
        }
    }

    /**
     * Types of interfaces to return from .
     */
    public enum InterfaceType {
        /**
         * Local and external interfaces.
         */
        ALL,

        /**
         * Only external interfaces.
         */
        EXTERNAL_ONLY,
    }

    /**
     * Returns a debug printable representation of a string list.
     */
    public static String debugString(List<String> stringList) {
        StringBuilder result = new StringBuilder("[");
        Iterator<String> listIter = stringList.iterator();
        if (listIter.hasNext()) {
            result.append('"');  // Opening quote for the first string
            result.append(listIter.next());
            while (listIter.hasNext()) {
                result.append("\", \"");
                result.append(listIter.next());
            }
            result.append('"');  // Closing quote for the last string
        }
        result.append(']');
        return result.toString();
    }

    /**
     * Returns a debug printable representation of a string array.
     */
    public static String debugString(String[] arr) {
        return debugString(Arrays.asList(arr));
    }

    public String getAppVersionName() {
        try {
            String packageName = context.getPackageName();
            return context.getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (Exception e) {
            Logger.e("version name of the application cannot be found", e);
        }
        return "Unknown";
    }

    /**
     * Returns the current battery level
     * */
    public synchronized int getCurrentBatteryLevel() {
        return curBatteryLevel;
    }

    /**
     * Returns if the batter is charing
     */
    public synchronized boolean isCharging() {
        return isCharging;
    }

    public synchronized int getTemperature(){return  temperature;}

    private synchronized void updateBatteryStat(Intent powerIntent) {
        int scale = powerIntent.getIntExtra(BatteryManager.EXTRA_SCALE,
                Config.DEFAULT_BATTERY_SCALE);
        int level = powerIntent.getIntExtra(BatteryManager.EXTRA_LEVEL,
                Config.DEFAULT_BATTERY_LEVEL);
        // change to the unit of percentage
        curBatteryLevel = level * 100 / scale;
        isCharging = powerIntent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN) == BatteryManager.BATTERY_STATUS_CHARGING;
        temperature = powerIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Config.DEFAULT_BATTERY_TEMPERATURE);

        Logger.i(
                "Current power level is " + curBatteryLevel + ", isCharging = " + isCharging + " and temperature = "+temperature);
    }

    /**
     * Sets the current RSSI value
     */
    public synchronized void setCurrentRssi(int rssi) {
        currentSignalStrength = rssi;
    }

    /**
     * Returns the last updated RSSI value
     */
    public synchronized int getCurrentRssi() {
        initNetwork();
        return currentSignalStrength;
    }

    private class SignalStrengthChangeListener extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            String network = getNetwork();
            if (network.equals(NETWORK_TYPES[TelephonyManager.NETWORK_TYPE_CDMA])) {
                setCurrentRssi(signalStrength.getCdmaDbm());
            } else if (network.equals(NETWORK_TYPES[TelephonyManager.NETWORK_TYPE_EVDO_0]) ||
                    network.equals(NETWORK_TYPES[TelephonyManager.NETWORK_TYPE_EVDO_A]) ||
                    network.equals(NETWORK_TYPES[TelephonyManager.NETWORK_TYPE_EVDO_B])) {
                setCurrentRssi(signalStrength.getEvdoDbm());
            } else if (signalStrength.isGsm()) {
                setCurrentRssi(signalStrength.getGsmSignalStrength());
            }
        }
    }

    /**
     * Fetches the new connectivity state from the connectivity manager directly.
     */
    private synchronized void updateConnectivityInfo() {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) {
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                PhoneUtils.this.currentNetworkConnection = TYPE_WIFI;
            }
            if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                PhoneUtils.this.currentNetworkConnection = TYPE_MOBILE;
            }
        } else {
            PhoneUtils.this.currentNetworkConnection = TYPE_NOT_CONNECTED;
        }
    }

    private class PowerStateChangeReceiver extends BroadcastReceiver {
        /**
         * @see BroadcastReceiver#onReceive(Context,
         * Intent)
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.i("Battery level changed");
            updateBatteryStat(intent);
        }
    }

    /**
     * When alerted that the network connectivity has changed, change the
     * stored connectivity value.
     */
    private class ConnectivityChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateConnectivityInfo();
            if ("android.net.conn.CONNECTIVITY_CHANGE".equals(intent.getAction())) {
                WebSocketConnector connector = WebSocketConnector.getInstance();
                SharedPreferences prefs = context.getSharedPreferences(Config.PREF_KEY_RESOLVED_TARGET, Context.MODE_PRIVATE);
                String target = prefs.getString(Config.PREF_KEY_RESOLVED_TARGET, null);
                if(target != null && !connector.isConnected())
                    connector.connectWebSocket(target);
                try {
                    DNSFilterService.possibleNetworkChange(false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public synchronized int getCurrentNetworkConnection() {
        return currentNetworkConnection;
    }

    private String getVersionStr() {
        return String.format("INCREMENTAL:%s, RELEASE:%s, SDK_INT:%s", Build.VERSION.INCREMENTAL,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
    }

    @SuppressLint("MissingPermission")
    public String getDeviceId() {
        String deviceId = null;
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
            deviceId = telephonyManager.getDeviceId();  // This ID is permanent to a physical phone.
        // "generic" means the emulator.
        if (deviceId == null || Build.DEVICE.equals("generic")) {
            // This ID changes on OS reinstall/factory reset.
            deviceId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        }
        return deviceId;
    }


    public DeviceInfo getDeviceInfo() {
        if (deviceInfo == null) {
            deviceInfo = new DeviceInfo();
            deviceInfo.deviceId = getDeviceId();
            deviceInfo.manufacturer = Build.MANUFACTURER;
            deviceInfo.model = Build.MODEL;
            deviceInfo.os = getVersionStr();
            deviceInfo.user = Build.VERSION.CODENAME;
        }

        return deviceInfo;
    }

    private Location getMockLocation() {
        return new Location("MockProvider");
    }

    public String getServerUrl() {
        return context.getResources().getString(R.string.serverUrl);
    }

    public String getAnonymousServerUrl() {
        return context.getResources().getString(R.string.anonymousServerUrl);
    }

    public String getTestingServerUrl() {
        return context.getResources().getString(R.string.testServerUrl);
    }

    public boolean isTestingServer(String serverUrl) {
        return serverUrl == getTestingServerUrl();
    }

    /**
     * Using MLab service to detect ipv4 or ipv6 compatibility
     *
     * @param ip_detect_type -- "ipv4" or "ipv6"
     * @return IP_TYPE_CANNOT_DECIDE, IP_TYPE_UNCONNECTIVITY, IP_TYPE_CONNECTIVITY
     */
    private int checkIPCompatibility(String ip_detect_type) {
        if (!ip_detect_type.equals("ipv4") && !ip_detect_type.equals("ipv6")) {
            return IP_TYPE_CANNOT_DECIDE;
        }
        Socket tcpSocket = new Socket();
        try {
            ArrayList<String> hostnameList = MLabNS.Lookup(context, "mobiperf",
                    ip_detect_type, "ip");
            // MLabNS returns at least one ip address
            if (hostnameList.isEmpty())
                return IP_TYPE_CANNOT_DECIDE;
            // Use the first result in the element
            String hostname = hostnameList.get(0);
            SocketAddress remoteAddr = new InetSocketAddress(hostname, portNum);
            tcpSocket.setTcpNoDelay(true);
            tcpSocket.connect(remoteAddr, tcpTimeout);
        } catch (ConnectException e) {
            // Server is not reachable due to client not support ipv6
            Logger.e("Connection exception is " + e.getMessage());
            return IP_TYPE_UNCONNECTIVITY;
        } catch (IOException e) {
            // Client timer expired
            Logger.e("Fail to setup TCP in checkIPCompatibility(). "
                    + e.getMessage());
            return IP_TYPE_CANNOT_DECIDE;
        } catch (InvalidParameterException e) {
            // MLabNS service lookup fail
            Logger.e("InvalidParameterException in checkIPCompatibility(). "
                    + e.getMessage());
            return IP_TYPE_CANNOT_DECIDE;
        } catch (IllegalArgumentException e) {
            Logger.e("IllegalArgumentException in checkIPCompatibility(). "
                    + e.getMessage());
            return IP_TYPE_CANNOT_DECIDE;
        } finally {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                Logger.e("Fail to close TCP in checkIPCompatibility().");
                return IP_TYPE_CANNOT_DECIDE;
            }
        }
        return IP_TYPE_CONNECTIVITY;
    }

    /**
     * Use MLabNS slices to check IPv4/IPv6 domain name resolvable
     *
     * @param ip_detect_type -- "ipv4" or "ipv6"
     * @return DN_UNRESOLVABLE, DN_RESOLVABLE
     */
    private int checkDomainNameResolvable(String ip_detect_type) {
        if (!ip_detect_type.equals("ipv4") && !ip_detect_type.equals("ipv6")) {
            return DN_UNKNOWN;
        }
        try {
            ArrayList<String> ipAddressList = MLabNS.Lookup(context, "mobiperf",
                    ip_detect_type, "fqdn");
            String ipAddress;
            // MLabNS returns one fqdn each time
            if (ipAddressList.size() == 1) {
                ipAddress = ipAddressList.get(0);
            } else {
                return DN_UNKNOWN;
            }
            InetAddress inet = InetAddress.getByName(ipAddress);
            if (inet != null)
                return DN_RESOLVABLE;
        } catch (UnknownHostException e) {
            // Fail to resolve domain name
            Logger.e("UnknownHostException in checkDomainNameResolvable() "
                    + e.getMessage());
            return DN_UNRESOLVABLE;
        } catch (InvalidParameterException e) {
            // Fail to resolve domain name
            Logger.e("InvalidParameterException in checkIPCompatibility(). "
                    + e.getMessage());
            return DN_UNRESOLVABLE;
        }
        return DN_UNKNOWN;
    }

    /**
     * Summarize ip connectable cases
     *
     * @return ipv4, ipv6, ipv4_ipv6, IP_TYPE_NONE or IP_TYPE_UNKNOWN
     */
    public String getIpConnectivity() {
        int v4Conn = checkIPCompatibility("ipv4");
        int v6Conn = checkIPCompatibility("ipv6");
        if (v4Conn == IP_TYPE_CONNECTIVITY && v6Conn == IP_TYPE_CONNECTIVITY)
            return IP_TYPE_IPV4_IPV6_BOTH;
        if (v4Conn == IP_TYPE_CONNECTIVITY && v6Conn != IP_TYPE_CONNECTIVITY)
            return IP_TYPE_IPV4_ONLY;
        if (v4Conn != IP_TYPE_CONNECTIVITY && v6Conn == IP_TYPE_CONNECTIVITY)
            return IP_TYPE_IPV6_ONLY;
        if (v4Conn == IP_TYPE_UNCONNECTIVITY && v6Conn == IP_TYPE_UNCONNECTIVITY)
            return IP_TYPE_NONE;
        return IP_TYPE_UNKNOWN;
    }

    /**
     * Summarize Domain Name resolvability cases
     *
     * @return ipv4, ipv6, ipv4_ipv6, IP_TYPE_NONE or IP_TYPE_UNKNOWN
     */
    public String getDnResolvability() {
        int v4Resv = checkDomainNameResolvable("ipv4");
        int v6Resv = checkDomainNameResolvable("ipv6");
        if (v4Resv == DN_RESOLVABLE && v6Resv == DN_RESOLVABLE)
            return IP_TYPE_IPV4_IPV6_BOTH;
        if (v4Resv == DN_RESOLVABLE && v6Resv != DN_RESOLVABLE)
            return IP_TYPE_IPV4_ONLY;
        if (v4Resv != DN_RESOLVABLE && v6Resv == DN_RESOLVABLE)
            return IP_TYPE_IPV6_ONLY;
        if (v4Resv == DN_UNRESOLVABLE && v6Resv == DN_UNRESOLVABLE)
            return IP_TYPE_NONE;
        return IP_TYPE_UNKNOWN;
    }

    private String convertIpAddress(int ip){
        int[] ipAddr = new int[4];
        ipAddr[0] = ip & 0xFF;
        ipAddr[1] = (ip >> 8) & 0xFF;
        ipAddr[2] = (ip >> 16) & 0xFF;
        ipAddr[3] = (ip >> 24) & 0xFF;
        return String.format("%s.%s.%s.%s", ipAddr[0], ipAddr[1], ipAddr[2], ipAddr[3]);
    }

    @SuppressLint("HardwareIds")
    public JSONObject getAccessPointInfo(){
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService (Context.WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo ();
        JSONObject accessPointInfo = new JSONObject();
        try {
            accessPointInfo.put("deviceId", getDeviceInfo().deviceId);
            accessPointInfo.put("BSSID", info.getBSSID());
            accessPointInfo.put("SSID", info.getSSID());
            accessPointInfo.put("frequency", info.getFrequency());
            accessPointInfo.put("ipAddress", convertIpAddress(info.getIpAddress()));
            accessPointInfo.put("linkSpeed", info.getLinkSpeed());
            accessPointInfo.put("macAddress", info.getMacAddress());
            accessPointInfo.put("rssi", info.getRssi());
            accessPointInfo.put("timestamp", System.currentTimeMillis() * 1000);
        } catch (JSONException e) {
            Logger.e("Error occurred while generating access point payload");
        }
        return accessPointInfo;
    }

    /**
     * Returns the DeviceProperty needed to report the measurement result
     */
    public DeviceProperty getDeviceProperty() {
        String carrierName = telephonyManager.getNetworkOperatorName();
        Location location;
        if (isTestingServer(getServerUrl())) {
            location = getMockLocation();
        } else {
            location = getLocation();
        }
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        String networkType = PhoneUtils.getPhoneUtils().getNetwork();
        String ipConnectivity = getIpConnectivity();
        String dnResolvability = getDnResolvability();
        Logger.w("IP connectivity is " + ipConnectivity);
        Logger.w("DN resolvability is " + dnResolvability);
        if (activeNetwork != null) {
            networkType = activeNetwork.getTypeName();
        }
        String versionName = PhoneUtils.getPhoneUtils().getAppVersionName();
        PhoneUtils utils = PhoneUtils.getPhoneUtils();

        return new DeviceProperty(getDeviceInfo().deviceId, versionName,
                System.currentTimeMillis() * 1000, getVersionStr(), ipConnectivity,
                dnResolvability, location.getLongitude(), location.getLatitude(),
                location.getProvider(), networkType, carrierName,
                utils.getCurrentBatteryLevel(), utils.isCharging(),
                utils.getCellInfo(false), utils.getCurrentRssi());
    }
}
