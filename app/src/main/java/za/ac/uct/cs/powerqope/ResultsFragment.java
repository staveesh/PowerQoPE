package za.ac.uct.cs.powerqope;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class ResultsFragment extends Fragment {
    public static final String TAB_TAG = "MY_MEASUREMENTS";

    private ListView consoleView;
    private ArrayAdapter<String> results;
    BroadcastReceiver receiver;
    ProgressBar progressBar;
    ToggleButton showUserResultButton;
    ToggleButton showSystemResultButton;
    MeasurementScheduler scheduler = null;
    boolean userResultsActive = false;

    private View v;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        v = inflater.inflate(R.layout.fragment_results, container, false);
        setHasOptionsMenu(true);
        Logger.d("ResultsFragment.onCreate called");
        IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateIntent.SCHEDULER_CONNECTED_ACTION);
        filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
        filter.addAction(UpdateIntent.RESULTS_UPDATE_VIEW);

        this.consoleView = v.findViewById(R.id.resultConsole);
        this.results = new ArrayAdapter<>(getActivity().getApplicationContext(), R.layout.list_item);
        this.consoleView.setAdapter(this.results);
        this.progressBar = (ProgressBar) v.findViewById(R.id.progress_bar);
        this.progressBar.setMax(Config.MAX_PROGRESS_BAR_VALUE);
        this.progressBar.setProgress(Config.MAX_PROGRESS_BAR_VALUE);
        showUserResultButton = (ToggleButton) v.findViewById(R.id.showUserResults);
        showSystemResultButton = (ToggleButton) v.findViewById(R.id.showSystemResults);
        showUserResultButton.setChecked(true);
        showSystemResultButton.setChecked(false);
        userResultsActive = true;

        // We enforce a either-or behavior between the two ToggleButtons
        CompoundButton.OnCheckedChangeListener buttonClickListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Logger.d("onCheckedChanged");
                switchBetweenResults((buttonView == showUserResultButton) == isChecked);
            }
        };
        showUserResultButton.setOnCheckedChangeListener(buttonClickListener);
        showSystemResultButton.setOnCheckedChangeListener(buttonClickListener);

        this.receiver = new BroadcastReceiver() {
            @Override
            // All onXyz() callbacks are single threaded
            public void onReceive(Context context, Intent intent) {
                if (UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION.equals(intent.getAction())) {
                    int progress = intent.getIntExtra(UpdateIntent.PROGRESS_PAYLOAD, Config.INVALID_PROGRESS);
                    int priority = intent.getIntExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD,
                            MeasurementTask.INVALID_PRIORITY);
                    // Show user results if there is currently a user measurement running
                    if (priority == MeasurementTask.USER_PRIORITY) {
                        Logger.d("progress update");
                        switchBetweenResults(true);
                    }
                    upgradeProgress(progress, Config.MAX_PROGRESS_BAR_VALUE);
                } else if (UpdateIntent.SCHEDULER_CONNECTED_ACTION.equals(intent.getAction())) {
                    Logger.d("scheduler connected");
                    switchBetweenResults(userResultsActive);
                }else if(UpdateIntent.RESULTS_UPDATE_VIEW.equals(intent.getAction())){
                    Log.d("Refresh Results", "Updating the results views");
                    getConsoleContentFromScheduler();
                }
            }
        };
        getActivity().getApplicationContext().registerReceiver(this.receiver, filter);

        getConsoleContentFromScheduler();
        return v;
    }

    /**
     * Change the underlying adapter for the ListView.
     *
     * @param showUserResults If true, show user results; otherwise, show system results.
     */
    private synchronized void switchBetweenResults(boolean showUserResults) {
        userResultsActive = showUserResults;
        getConsoleContentFromScheduler();
        showUserResultButton.setChecked(showUserResults);
        showSystemResultButton.setChecked(!showUserResults);
        Logger.d("switchBetweenResults: showing " + results.getCount() + " " +
                (showUserResults ? "user" : "system") + " results");
    }

    /**
     * Upgrades the progress bar in the UI.
     */
    private void upgradeProgress(int progress, int max) {
        Logger.d("Progress is " + progress);
        if (progress >= 0 && progress <= max) {
            progressBar.setProgress(progress);
            this.progressBar.setVisibility(View.VISIBLE);
        } else {
            // UserMeasurementTask broadcast a progress greater than max to indicate the termination of
            // the measurement.
            this.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        Logger.d("ResultsFragment.onDestroy called");
        super.onDestroy();
        getActivity().getApplicationContext().unregisterReceiver(this.receiver);
    }

    private synchronized void getConsoleContentFromScheduler() {
        Logger.d("ResultsFragment.getConsoleContentFromScheduler called");
        if (scheduler == null) {
            MainActivity parent = MainActivity.getCurrentApp();
            scheduler = parent.getScheduler();
        }
        // Scheduler may have not had time to start yet. When it does, the intent above will call this
        // again.
        if (scheduler != null) {
            Logger.d("Updating measurement results from thread " + Thread.currentThread().getName());
            results.clear();
            final List<String> scheduler_results =
                    (userResultsActive ? scheduler.getUserResults() : scheduler.getSystemResults());
            for (String result : scheduler_results) {
                results.add(result);
            }
            if(getActivity()!=null)
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        results.notifyDataSetChanged();
                        Log.i("Update UI","Notified");
                    }
                });
        }
    }
}
