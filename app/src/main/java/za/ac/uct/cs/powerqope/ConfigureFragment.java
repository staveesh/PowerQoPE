package za.ac.uct.cs.powerqope;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ConfigureFragment extends Fragment {
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
    }

}
