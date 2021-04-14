package za.ac.uct.cs.powerqope;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class WelcomeScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome_screen);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent();
            intent.setClassName(WelcomeScreen.this.getApplicationContext(),
                    MainActivity.class.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            WelcomeScreen.this.getApplication().startActivity(intent);
            WelcomeScreen.this.finish();
        }, Config.WELCOME_SCREEN_DURATION_MSEC);
    }
}