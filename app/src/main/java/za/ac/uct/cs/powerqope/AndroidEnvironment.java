/*
 PersonalDNSFilter 1.5
 Copyright (C) 2017-2020 Ingo Zenz

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

 Find the latest version at http://www.zenz-solutions.de/personaldnsfilter
 Contact:i.z@gmx.net
 */

package za.ac.uct.cs.powerqope;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

import za.ac.uct.cs.powerqope.dns.DNSFilterService;
import za.ac.uct.cs.powerqope.util.ExecutionEnvironment;
import za.ac.uct.cs.powerqope.util.ExecutionEnvironmentInterface;
import za.ac.uct.cs.powerqope.util.Util;

public class AndroidEnvironment implements ExecutionEnvironmentInterface {

    private static final String TAG = "AndroidEnvironment";

    private static Context ctx = null;
    private static AndroidEnvironment INSTANCE = new AndroidEnvironment();
    private static String WORKDIR;
    private static Stack wakeLooks = new Stack();

    static {
        ExecutionEnvironment.setEnvironment(INSTANCE);
    }


    public static void initEnvironment(Context context) {
        ctx = context;
        if (Build.VERSION.SDK_INT >= 19) {
            context.getExternalFilesDirs(null); //Seems on some devices this has to be called once before accessing Files...
            WORKDIR = context.getExternalFilesDirs (null)[0].getAbsolutePath() + "/DNSFilter";
        }
        else
            WORKDIR= Environment.getExternalStorageDirectory().getAbsolutePath() + "/DNSFilter";
    }

    @Override
    public int getEnvironmentID() {
        return 1;
    }

    @Override
    public String getEnvironmentVersion() {
        return ""+ Build.VERSION.SDK_INT;
    }

    @Override
    public void wakeLock(){
        WifiManager.WifiLock wifiLock = ((WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "personalHttpProxy");
        wifiLock.acquire();
        PowerManager.WakeLock wakeLock = ((PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "personalDNSfilter:wakelock");
        wakeLock.acquire();
        wakeLooks.push(new Object[]{wifiLock, wakeLock});
        Log.i(TAG,"Aquired WIFI lock and partial wake lock!");
    }

    @Override
    public void releaseWakeLock() {
        Object[] locks;
        try {
            locks = (Object[]) wakeLooks.pop();
        } catch (Exception e) {
            Log.e(TAG,e.getMessage());
            return;
        }
        WifiManager.WifiLock wifiLock = (WifiManager.WifiLock) locks[0];
        PowerManager.WakeLock wakeLock = (PowerManager.WakeLock) locks[1];
        wifiLock.release();
        wakeLock.release();
        Log.i(TAG,"Released WIFI lock and partial wake lock!");
    }

    @Override
    public void releaseAllWakeLocks() {
        Object[] locks;
        while (!wakeLooks.isEmpty()) {
            try {
                locks = (Object[]) wakeLooks.pop();
            } catch (Exception e) {
                Log.e(TAG,e.getMessage());
                return;
            }
            WifiManager.WifiLock wifiLock = (WifiManager.WifiLock) locks[0];
            PowerManager.WakeLock wakeLock = (PowerManager.WakeLock) locks[1];
            wifiLock.release();
            wakeLock.release();
            Log.i(TAG,"Released WIFI lock and partial wake lock!");
        }
    }

    @Override
    public String getWorkDir() {
        return WORKDIR;
    }

    @Override
    public void onReload() throws IOException {
        DNSFilterService.onReload();
    }

    @Override
    public InputStream getAsset(String path) throws IOException {
        AssetManager assetManager = ctx.getAssets();
        return(assetManager.open(path));
    }

    @Override
    public boolean hasNetwork() {
        ConnectivityManager conMan= (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = conMan.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    @Override
    public boolean protectSocket(Object socket, int type) {
        return DNSFilterService.protectSocket(socket, type);
    }

    @Override
    public void migrateConfig() throws IOException {

        //TO BE DELETED ONCE ON TARGET 11! MIGRATION OF CONFIG DATA TO EXTERNAL USER FOLDER

        boolean storagePermission = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePermission = false;
                //requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                //Log.i(TAG,"Need storage permissions to start!");
            }
        }

        //TO BE DELETED ONCE ON TARGET 11! MIGRATION OF CONFIG DATA TO EXTERNAL USER FOLDER
        File F_WORKDIR = new File(WORKDIR);
        if (!F_WORKDIR.exists() && storagePermission) {
            File OLDPATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter");
            if (OLDPATH.exists() && !OLDPATH.equals(F_WORKDIR)) {
                try {
                    Util.moveFileTree(OLDPATH, F_WORKDIR);
                    Log.i(TAG,"MIGRATED old config location to app storage!");
                    Log.i(TAG,"NEW FOLDER: "+F_WORKDIR);
                } catch (IOException eio) {
                    Log.i(TAG,"Migration of old config location has failed!");
                    Log.e(TAG, eio.getMessage());
                }
            }
        }

    }

}
