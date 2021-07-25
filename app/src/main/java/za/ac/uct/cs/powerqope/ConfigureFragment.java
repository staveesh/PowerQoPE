package za.ac.uct.cs.powerqope;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;

import za.ac.uct.cs.powerqope.util.PhoneUtils;

public class ConfigureFragment extends Fragment {

    private static final String TAG = "ConfigureFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RadioGroup rg = getView().findViewById(R.id.security_options_radio);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton rb = getView().findViewById(checkedId);
                boolean checked = rb.isChecked();
                switch (checkedId){
                    case R.id.radio_high:
                        if (checked){
                            Toast.makeText(getContext(), "High security selected", Toast.LENGTH_LONG).show();
                        }
                        break;
                    case R.id.radio_medium:
                        if (checked){
                            Toast.makeText(getContext(), "Medium security selected", Toast.LENGTH_LONG).show();
                        }
                        break;
                    case R.id.radio_low:
                        if (checked){
                            Toast.makeText(getContext(), "Low security selected", Toast.LENGTH_LONG).show();
                        }
                        break;
                    case R.id.radio_advanced:
                        if(checked){
                            Toast.makeText(getContext(), "Advanced security options selected", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(getActivity(), SettingsActivity.class);
                            startActivity(intent);
                        }
                        break;
                }
            }
        });
        Switch applySwitch = getView().findViewById(R.id.toggleSettings);
        applySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked && rg.getCheckedRadioButtonId() == -1){
                Toast.makeText(getContext(), "Select at least one configuration!", Toast.LENGTH_LONG).show();
                applySwitch.setChecked(false);
            }
            else if(isChecked){
                String configuration;
                switch (rg.getCheckedRadioButtonId()){
                    case R.id.radio_high:
                        configuration = "high";
                        break;
                    case R.id.radio_medium:
                        configuration = "medium";
                        break;
                    case R.id.radio_low:
                        configuration = "low";
                        break;
                    default:
                        configuration = "advanced";
                }
                if(!configuration.equals("advanced")) {
                    WebSocketConnector connector = WebSocketConnector.getInstance();
                    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("level", configuration);
                        payload.put("networkType", phoneUtils.getNetworkClass());
                        payload.put("deviceId", connector.getDeviceId());
                    } catch (JSONException e) {
                        Log.e(TAG, "onViewCreated: Error while building JSON");
                    }
                    connector.sendMessage(Config.STOMP_SERVER_CONFIG_REQUEST_ENDPOINT, payload.toString());
                }
            }
            else{
                // Default configuration with Do53, no filters, no VPN, default ciphers
                // Clear radio buttons
                Log.i(TAG, "onViewCreated: Default configuration");
            }
        });
    }

}
