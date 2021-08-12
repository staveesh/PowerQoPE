package za.ac.uct.cs.powerqope;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import io.reactivex.CompletableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompHeader;
import za.ac.uct.cs.powerqope.dns.ConfigurationAccess;
import za.ac.uct.cs.powerqope.util.MeasurementJsonConvertor;
import za.ac.uct.cs.powerqope.util.Util;

public class WebSocketConnector {

    private static final String TAG = "WebSocketConnector";

    private static MeasurementScheduler scheduler;
    private static Context context;
    private StompClient mStompClient;
    private CompositeDisposable compositeDisposable;
    private static WebSocketConnector instance;
    private ConfigurationAccess CONFIG = ConfigurationAccess.getLocal();

    private WebSocketConnector() {
    }

    public static WebSocketConnector getInstance() {
        if (instance == null) {
            instance = new WebSocketConnector();
        }
        return instance;
    }

    public static synchronized void setContext(Context newContext){
        assert newContext != null;
        context = newContext;
    }

    public static synchronized void setScheduler(MeasurementScheduler schedulerInstance){
        assert scheduler == null;
        scheduler = schedulerInstance;
    }

    private List<Disposable> getSubscriptions(){
        return new ArrayList<Disposable>(){{
            add(subscribeToNewJobs());
            add(subscribeToSecurityConfig());
        }};
    }

    private Disposable subscribeToNewJobs(){
        String deviceId = getDeviceId();
        return subscribeToTopic(String.format(Config.STOMP_SERVER_TASKS_ENDPOINT, deviceId), result -> {
            Vector<MeasurementTask> tasksFromServer = new Vector<>();
            JSONArray jsonArray = null;
            try {
                jsonArray = new JSONArray(result.getPayload());
                for (int i = 0; i < jsonArray.length(); i++) {
                    Logger.d("Parsing index " + i);
                    JSONObject json = jsonArray.optJSONObject(i);
                    Logger.d("Value is " + json);
                    if (json != null && MeasurementTask.getMeasurementTypes().contains(json.get("type"))) {
                        try {
                            MeasurementTask task = MeasurementJsonConvertor.makeMeasurementTaskFromJson(json, context);
                            Logger.i(MeasurementJsonConvertor
                                    .toJsonString(task.measurementDesc));
                            tasksFromServer.add(task);
                        } catch (IllegalArgumentException e) {
                            Logger.w("Could not create task from JSON: " + e);
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON received from server");
            }
            scheduler.updateSchedule(tasksFromServer, false);
            scheduler.handleMeasurement();
        });
    }

    private void modifyConfig(JSONObject filter, JSONObject cipher){
        try {
            boolean changed = false;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String ln;
            String filterValue;
            switch (filter.getString("dnsType")){
                case "dot":
                    filterValue = filter.getString("ipAddress")+"::853::DoT";
                    break;
                case "doh":
                    String url = filter.getString("url");
                    if(url.contains("https")){
                        url = url.substring(7);
                    }
                    filterValue = filter.getString("ipAddress")+"::443::DoH::"+filter.getString("url");

                    break;
                default:
                    filterValue = filter.getString("ipAddress");
                    break;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(CONFIG.readConfig())));
            while ((ln = reader.readLine()) != null) {
                String old = ln;

                if (ln.trim().startsWith("detectDNS"))
                    ln = "detectDNS = " + false;

                else if(ln.trim().startsWith("fallbackDNS"))
                    ln = "fallbackDNS = "+filterValue;

                else if(ln.trim().startsWith("cipher"))
                    ln = "cipher = "+cipher.getString("tlsVersion")+"::"+cipher.getString("name");

                out.write((ln + "\r\n").getBytes());

                changed = changed || !old.equals(ln);
            }

            reader.close();
            out.flush();
            out.close();

            if (changed) {
                CONFIG.updateConfig(out.toByteArray());
            }

        } catch (Exception e){
            Log.e(TAG, "persistConfig: "+e.getMessage());
        }
    }

    private Disposable subscribeToSecurityConfig(){
        String deviceId = getDeviceId();
        return subscribeToTopic(String.format(Config.STOMP_SERVER_CONFIG_RESPONSE_ENDPOINT, deviceId), result -> {
            try {
                JSONObject config = new JSONObject(result.getPayload());
                JSONObject filter = config.getJSONObject("filter");
                JSONObject cipher = config.getJSONObject("cipher");
                // Write filter to file
                modifyConfig(filter, cipher);
            } catch (JSONException e) {
                Log.e(TAG, "subscribeToSecurityConfig: Error parsing JSON from server");
            }

        });
    }

    public void connectWebSocket(String target) {
        if(target == null)
            return;
        String deviceId = getDeviceId();
        OkHttpClient client = new OkHttpClient.Builder()
                .hostnameVerifier(new HostnameVerifier() {
                    @SuppressLint("BadHostnameVerifier")
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                })
                .build();
        mStompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, target, null, client);
        List<StompHeader> headers = new ArrayList<StompHeader>() {{
            add(new StompHeader("deviceId", deviceId));
        }};
        resetSubscriptions();

        Disposable dispLifecycle = mStompClient.lifecycle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lifecycleEvent -> {
                    switch (lifecycleEvent.getType()) {
                        case OPENED:
                            Log.d(TAG, "Stomp connection opened");
                            break;
                        case ERROR:
                            Log.e(TAG, "Stomp connection error", lifecycleEvent.getException());
                            Log.d(TAG, "Stomp connection error");
                            break;
                        case CLOSED:
                            Log.d(TAG, "Stomp connection closed");
                            resetSubscriptions();
                            break;
                        case FAILED_SERVER_HEARTBEAT:
                            Log.d(TAG, "Stomp failed server heartbeat");
                            break;
                    }
                });

        compositeDisposable.add(dispLifecycle);
        List<Disposable> subscriptions = getSubscriptions();
        for(Disposable subscription : subscriptions)
            compositeDisposable.add(subscription);
        mStompClient.connect(headers);
    }

    public String getDeviceId() {
        String uuid;
        SharedPreferences uniqueIdPref = context.getSharedPreferences(Config.PREF_KEY_UNIQUE_ID, Context.MODE_PRIVATE);
        uuid = uniqueIdPref.getString(Config.PREF_KEY_UNIQUE_ID, null);
        if(uuid == null) {
            uuid = UUID.randomUUID().toString()+"_"+ Util.hashTimeStamp();
            SharedPreferences.Editor edit = uniqueIdPref.edit();
            edit.putString(Config.PREF_KEY_UNIQUE_ID, uuid);
            edit.apply();
        }
        return uuid;
    }

    public void sendMessage(String endpoint, String content) {
        compositeDisposable.add(mStompClient.send(endpoint, content)
                .compose(applySchedulers())
                .subscribe(
                        () -> Log.d(TAG, String.format("Message sent successfully to %s", endpoint)),
                        (throwable) -> Log.d(TAG, String.format("Error sending message to %s", endpoint, throwable))
                ));
    }

    protected CompletableTransformer applySchedulers() {
        return upstream -> upstream
                .unsubscribeOn(Schedulers.newThread())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void resetSubscriptions() {
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
        compositeDisposable = new CompositeDisposable();
    }

    public void disconnect() {
        mStompClient.disconnect();
    }

    public boolean isConnected() {
        if(mStompClient == null) return false;
        return mStompClient.isConnected();
    }

    private Disposable subscribeToTopic(String endpoint, SubscriptionCallbackInterface callback){
        return mStompClient.topic(endpoint)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(callback::onSubscriptionResult, throwable -> {
                    Log.e(TAG, "Error on subscribe topic", throwable);
                });
    }
}
