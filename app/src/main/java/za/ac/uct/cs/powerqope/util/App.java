package za.ac.uct.cs.powerqope.util;

import android.app.Application;
import android.content.Context;

public class App extends Application {

    private static App instance;
    private static final String PROPERTY_ID = "UA-89622148-1";
    private static final String PROPERTY_ID_PRO = "UA-89641705-1";

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    public static String getResourceString(int resId) {
        return instance.getString(resId);
    }

    public static App getInstance() {
        return instance;
    }

}
