package za.ac.uct.cs.powerqope;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        switch(view.getId()) {
            case R.id.radio_high:
                if (checked){
                    Toast.makeText(getApplicationContext(), "High security selected", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.radio_medium:
                if (checked){
                    Toast.makeText(getApplicationContext(), "Medium security selected", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.radio_low:
                if (checked){
                    Toast.makeText(getApplicationContext(), "Low security selected", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.radio_advanced:
                if(checked){
                    Toast.makeText(getApplicationContext(), "Advanced security options selected", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }
                break;

        }
    }
}