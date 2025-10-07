package jonas.tool.saveForOffline;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setSubtitle("Preferences");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            super.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        private String list_appearance;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            list_appearance = getPreferenceScreen().getSharedPreferences().getString("layout", "1");
            disableEnablePreferences();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (!sharedPreferences.getString("layout", "1").equals(list_appearance)) {
                requireActivity().setResult(RESULT_FIRST_USER);
            } else if ("dark_mode".equals(key)) {
                requireActivity().setResult(RESULT_FIRST_USER);
            } else {
                requireActivity().setResult(RESULT_OK);
            }
            disableEnablePreferences();
        }

        private void disableEnablePreferences() {
            //this can probably be done through Preference dependencies, too lazy to figure out how..
            boolean useCustomStorageDirEnabled = getPreferenceScreen().getSharedPreferences().getBoolean("is_custom_storage_dir", true);
            Preference customStorageDirPref = findPreference("custom_storage_dir");
            if (customStorageDirPref != null) {
                customStorageDirPref.setEnabled(useCustomStorageDirEnabled);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }
}