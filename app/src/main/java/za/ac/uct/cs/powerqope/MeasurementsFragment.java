package za.ac.uct.cs.powerqope;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.security.InvalidParameterException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import za.ac.uct.cs.powerqope.measurements.PingTask;
import za.ac.uct.cs.powerqope.measurements.PingTask.PingDesc;
import za.ac.uct.cs.powerqope.measurements.TCPThroughputTask;
import za.ac.uct.cs.powerqope.measurements.TCPThroughputTask.TCPThroughputDesc;
import za.ac.uct.cs.powerqope.measurements.UDPBurstTask;
import za.ac.uct.cs.powerqope.measurements.UDPBurstTask.UDPBurstDesc;
import za.ac.uct.cs.powerqope.measurements.TracerouteTask;
import za.ac.uct.cs.powerqope.measurements.TracerouteTask.TracerouteDesc;
import za.ac.uct.cs.powerqope.measurements.DnsLookupTask;
import za.ac.uct.cs.powerqope.measurements.DnsLookupTask.DnsLookupDesc;
import za.ac.uct.cs.powerqope.measurements.HttpTask;
import za.ac.uct.cs.powerqope.measurements.HttpTask.HttpDesc;

public class MeasurementsFragment extends Fragment {
    private static final int NUMBER_OF_COMMON_VIEWS = 1;
    public static final String TAB_TAG = "MEASUREMENT_CREATION";

    private MainActivity parent;
    private String measurementTypeUnderEdit;
    private ArrayAdapter<String> spinnerValues;
    private String udpDir;
    private String tcpDir;
    private View v;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        v = inflater.inflate(R.layout.fragment_measurement, container, false);
        setHasOptionsMenu(true);
        if (v.getParent()!=null && (v.getParent().getClass().getName().compareTo("MainActivity") != 0))
            throw new AssertionError();
        this.parent = MainActivity.getCurrentApp();

        /*set the value of MEASUREMENT_CREATION_CONTEXT to this*/

        /* Initialize the measurement type spinner */
        Spinner spinner = v.findViewById(R.id.measurementTypeSpinner);
        spinnerValues = new ArrayAdapter<>(v.getContext(), R.layout.spinner_layout);
        for (String name : MeasurementTask.getMeasurementNames()) {
            // adding list of visible measurements
            if (MeasurementTask.getVisibilityForMeasurementName(name)) {
                spinnerValues.add(name);
            }
        }
        spinnerValues.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerValues);
        spinner.setOnItemSelectedListener(new MeasurementTypeOnItemSelectedListener());
        spinner.requestFocus();
        /* Setup the 'run' button */
        Button runButton = v.findViewById(R.id.runTaskButton);
        runButton.setOnClickListener(new ButtonOnClickListener());

        this.measurementTypeUnderEdit = PingTask.TYPE;
        setupEditTextFocusChangeListener();

        this.udpDir = "Up";
        this.tcpDir = "Up";

        final RadioButton radioUDPUp = v.findViewById(R.id.UDPBurstUpButton);
        final RadioButton radioUDPDown = v.findViewById(R.id.UDPBurstDownButton);
        final RadioButton radioTCPUp = v.findViewById(R.id.TCPThroughputUpButton);
        final RadioButton radioTCPDown = v.findViewById(R.id.TCPThroughputDownButton);

        radioUDPUp.setChecked(true);
        radioUDPUp.setOnClickListener(new UDPRadioOnClickListener());
        radioUDPDown.setOnClickListener(new UDPRadioOnClickListener());

        Button udpSettings = v.findViewById(R.id.UDPSettingsButton);
        udpSettings.setOnClickListener(new UDPSettingsOnClickListener());

        radioTCPUp.setChecked(true);
        radioTCPUp.setOnClickListener(new TCPRadioOnClickListener());
        radioTCPDown.setOnClickListener(new TCPRadioOnClickListener());
        return v;
    }

    private void setupEditTextFocusChangeListener() {
        EditBoxFocusChangeListener textFocusChangeListener = new EditBoxFocusChangeListener();
        EditText text = v.findViewById(R.id.pingTargetText);
        text.setOnFocusChangeListener(textFocusChangeListener);
        text = v.findViewById(R.id.tracerouteTargetText);
        text.setOnFocusChangeListener(textFocusChangeListener);
        text = v.findViewById(R.id.httpUrlText);
        text.setOnFocusChangeListener(textFocusChangeListener);
        text = v.findViewById(R.id.dnsLookupText);
        text.setOnFocusChangeListener(textFocusChangeListener);
        text = v.findViewById(R.id.UDPBurstIntervalText);
        text.setOnFocusChangeListener(textFocusChangeListener);
        text = v.findViewById(R.id.UDPBurstPacketCountText);
        text.setOnFocusChangeListener(textFocusChangeListener);
        text = v.findViewById(R.id.UDPBurstPacketSizeText);
        text.setOnFocusChangeListener(textFocusChangeListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.populateMeasurementSpecificArea();
    }

    private void clearMeasurementSpecificViews(TableLayout table) {
        for (int i = NUMBER_OF_COMMON_VIEWS; i < table.getChildCount(); i++) {
            View v = table.getChildAt(i);
            v.setVisibility(View.GONE);
        }
    }

    private void populateMeasurementSpecificArea() {
        TableLayout table = v.findViewById(R.id.measurementCreationLayout);
        this.clearMeasurementSpecificViews(table);
        if (this.measurementTypeUnderEdit.compareTo(PingTask.TYPE) == 0) {
            v.findViewById(R.id.pingView).setVisibility(View.VISIBLE);
        } else if (this.measurementTypeUnderEdit.compareTo(HttpTask.TYPE) == 0) {
            v.findViewById(R.id.httpUrlView).setVisibility(View.VISIBLE);
        } else if (this.measurementTypeUnderEdit.compareTo(TracerouteTask.TYPE) == 0) {
            v.findViewById(R.id.tracerouteView).setVisibility(View.VISIBLE);
        } else if (this.measurementTypeUnderEdit.compareTo(DnsLookupTask.TYPE) == 0) {
            v.findViewById(R.id.dnsTargetView).setVisibility(View.VISIBLE);
        } else if (this.measurementTypeUnderEdit.compareTo(UDPBurstTask.TYPE) == 0) {
            v.findViewById(R.id.UDPBurstDirView).setVisibility(View.VISIBLE);
            v.findViewById(R.id.UDPSettingsButton).setVisibility(View.VISIBLE);
//      v.findViewById(R.id.UDPBurstPacketSizeView).setVisibility(View.VISIBLE);
//      v.findViewById(R.id.UDPBurstPacketCountView).setVisibility(View.VISIBLE);
//      v.findViewById(R.id.UDPBurstIntervalView).setVisibility(View.VISIBLE);
        } else if (this.measurementTypeUnderEdit.compareTo(TCPThroughputTask.TYPE) == 0) {
            v.findViewById(R.id.TCPThroughputDirView).setVisibility(View.VISIBLE);
        }
    }

    private void hideKeyboard(EditText textBox) {
        if (textBox != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(textBox.getWindowToken(), 0);
        }
    }
    private class UDPSettingsOnClickListener implements View.OnClickListener {
        private boolean isShowSettings = false;
        @Override
        public void onClick(View v) {
            Button b = (Button)v;
            if (!isShowSettings) {
                isShowSettings = true;
                b.setText(getString(R.string.Collapse_Advanced_Settings));
                v.findViewById(R.id.UDPBurstPacketSizeView).setVisibility(View.VISIBLE);
                v.findViewById(R.id.UDPBurstPacketCountView).setVisibility(View.VISIBLE);
                v.findViewById(R.id.UDPBurstIntervalView).setVisibility(View.VISIBLE);
            }
            else {
                isShowSettings = false;
                b.setText(getString(R.string.Expand_Advanced_Settings));
                v.findViewById(R.id.UDPBurstPacketSizeView).setVisibility(View.GONE);
                v.findViewById(R.id.UDPBurstPacketCountView).setVisibility(View.GONE);
                v.findViewById(R.id.UDPBurstIntervalView).setVisibility(View.GONE);
            }
        }
    }

    private class UDPRadioOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            RadioButton rb = (RadioButton) v;
            MeasurementsFragment.this.udpDir = (String) rb.getText();
        }
    }

    private class TCPRadioOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            RadioButton rb = (RadioButton) v;
            MeasurementsFragment.this.tcpDir = (String) rb.getText();
        }
    }

    private class ButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            MeasurementTask newTask = null;
            boolean showLengthWarning = false;
            try {
                switch (measurementTypeUnderEdit) {
                    case PingTask.TYPE: {
                        EditText pingTargetText = getView().findViewById(R.id.pingTargetText);
                        Map<String, String> params = new HashMap<>();
                        params.put("target", pingTargetText.getText().toString());
                        PingDesc desc = new PingDesc(null,
                                Calendar.getInstance().getTime(),
                                null,
                                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                                Config.DEFAULT_USER_MEASUREMENT_COUNT,
                                MeasurementTask.USER_PRIORITY,
                                params, 1);
                        newTask = new PingTask(desc, getActivity().getApplicationContext());
                        break;
                    }
                    case HttpTask.TYPE: {
                        EditText httpUrlText = getView().findViewById(R.id.httpUrlText);
                        Map<String, String> params = new HashMap<>();
                        params.put("url", httpUrlText.getText().toString());
                        params.put("method", "get");
                        HttpDesc desc = new HttpDesc(null,
                                Calendar.getInstance().getTime(),
                                null,
                                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                                Config.DEFAULT_USER_MEASUREMENT_COUNT,
                                MeasurementTask.USER_PRIORITY,
                                params, 1);
                        newTask = new HttpTask(desc, getActivity().getApplicationContext());
                        break;
                    }
                    case TracerouteTask.TYPE: {
                        EditText targetText = getView().findViewById(R.id.tracerouteTargetText);
                        Map<String, String> params = new HashMap<>();
                        params.put("target", targetText.getText().toString());
                        TracerouteDesc desc = new TracerouteDesc(null,
                                Calendar.getInstance().getTime(),
                                null,
                                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                                Config.DEFAULT_USER_MEASUREMENT_COUNT,
                                MeasurementTask.USER_PRIORITY,
                                params, 1);
                        newTask =
                                new TracerouteTask(desc, getActivity().getApplicationContext());
                        showLengthWarning = true;
                        break;
                    }
                    case DnsLookupTask.TYPE: {
                        EditText dnsTargetText = getView().findViewById(R.id.dnsLookupText);
                        Map<String, String> params = new HashMap<>();
                        params.put("target", dnsTargetText.getText().toString());
                        DnsLookupDesc desc = new DnsLookupDesc(null,
                                Calendar.getInstance().getTime(),
                                null,
                                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                                Config.DEFAULT_USER_MEASUREMENT_COUNT,
                                MeasurementTask.USER_PRIORITY,
                                params, 1);
                        newTask =
                                new DnsLookupTask(desc, getActivity().getApplicationContext());
                        break;
                    }
                    case UDPBurstTask.TYPE: {
                        Map<String, String> params = new HashMap<>();
                        // TODO(dominic): Support multiple servers for UDP. For now, just
                        // m-lab.
                        params.put("target", "custom");
                        params.put("direction", udpDir);
                        // Get UDP Burst packet size
                        EditText UDPBurstPacketSizeText =
                                getView().findViewById(R.id.UDPBurstPacketSizeText);
                        params.put("packet_size_byte"
                                , UDPBurstPacketSizeText.getText().toString());
                        // Get UDP Burst packet count
                        EditText UDPBurstPacketCountText =
                                getView().findViewById(R.id.UDPBurstPacketCountText);
                        params.put("packet_burst"
                                , UDPBurstPacketCountText.getText().toString());
                        // Get UDP Burst interval
                        EditText UDPBurstIntervalText =
                                getView().findViewById(R.id.UDPBurstIntervalText);
                        params.put("udp_interval"
                                , UDPBurstIntervalText.getText().toString());

                        UDPBurstDesc desc = new UDPBurstDesc(null,
                                Calendar.getInstance().getTime(),
                                null,
                                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                                Config.DEFAULT_USER_MEASUREMENT_COUNT,
                                MeasurementTask.USER_PRIORITY,
                                params, 1);
                        newTask = new UDPBurstTask(desc
                                , getActivity().getApplicationContext());
                        break;
                    }
                    case TCPThroughputTask.TYPE: {
                        Map<String, String> params = new HashMap<>();
                        params.put("target", "custom");
                        params.put("dir_up", tcpDir);
                        TCPThroughputDesc desc = new TCPThroughputDesc(null,
                                Calendar.getInstance().getTime(),
                                null,
                                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                                Config.DEFAULT_USER_MEASUREMENT_COUNT,
                                MeasurementTask.USER_PRIORITY,
                                params, 1);
                        newTask = new TCPThroughputTask(desc,
                                getActivity().getApplicationContext());
                        showLengthWarning = true;
                        break;
                    }
                }

                if (newTask != null) {
                    MeasurementScheduler scheduler = parent.getScheduler();
                    if (scheduler != null && scheduler.submitTask(newTask)) {
                        /*
                         * Broadcast an intent with MEASUREMENT_ACTION so that the scheduler will immediately
                         * handles the user measurement
                         */
                        getActivity().getApplicationContext().sendBroadcast(
                                new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION));
                        MainActivity parent =MainActivity.getCurrentApp();
                        String toastStr =
                                MeasurementsFragment.this.getString(R.string.userMeasurementSuccessToast);
                        if (showLengthWarning) {
                            toastStr += newTask.getDescriptor() + " measurements can be long. Please be patient.";
                        }
                        Toast.makeText(getView().getContext(), toastStr, Toast.LENGTH_LONG).show();

                        if (scheduler.getCurrentTask() != null) {
                            showBusySchedulerStatus();
                        }
                    } else {
                        Toast.makeText(getView().getContext(), R.string.userMeasurementFailureToast,
                                Toast.LENGTH_LONG).show();
                    }
                }
            } catch (InvalidParameterException e) {
                Logger.e("InvalidParameterException when creating user measurements", e);
                Toast.makeText(getView().getContext(),
                        R.string.invalidParameterExceptionMeasurementToast +
                                ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }

    }

    private void showBusySchedulerStatus() {
        Intent intent = new Intent();
        intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
        intent.putExtra(
                UpdateIntent.STATUS_MSG_PAYLOAD, getString(R.string.userMeasurementBusySchedulerToast));
        getActivity().getApplicationContext().sendBroadcast(intent);
    }

    private class EditBoxFocusChangeListener implements View.OnFocusChangeListener {

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            switch (v.getId()) {
                case R.id.pingTargetText:
                    break;
                case R.id.httpUrlText:
                    break;
                default:
                    break;
            }
            if (!hasFocus) {
                hideKeyboard((EditText) v);
            }
        }
    }

    private class MeasurementTypeOnItemSelectedListener implements AdapterView.OnItemSelectedListener {

        /*
         * Handles the ItemSelected event in the MeasurementType spinner. Populate the measurement
         * specific area based on user input
         */
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            measurementTypeUnderEdit =
                    MeasurementTask.getTypeForMeasurementName(spinnerValues.getItem((int) id));
            if (measurementTypeUnderEdit != null) {
                populateMeasurementSpecificArea();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
