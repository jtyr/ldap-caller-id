/*
 * This file is part of the LDAP Caller ID application.
 *
 * LDAP Caller ID is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LDAP Caller ID is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LDAP Caller ID. If not, see <http://www.gnu.org/licenses/>.
 */

package cz.tyr.android.ldapcallerid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * 
 * Preference activity which handles the settings of the application.
 * 
 * @author Jiri Tyr
 *
 */
public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    private static final String TAG = "LDAPCallerID: PreferencesActivity";
    private static int DEBUG = 0;
    private static Context mContext = null;
    private static SharedPreferences mPrefs = null;

    /**
     * Preferences file name.
     */
    public static final String PREFS_NAME = PreferencesActivity.class.getPackage().getName() + "_preferences";
    // Path where to store all profiles
    private final static String profilePath = Environment.getExternalStorageDirectory().getPath() + "/LDAPCallerID/";
    // Path where is stored the shared preference file - !!!should be replaced by some system variable!!!
    private final String sharedPrefsPath = Environment.getDataDirectory().getPath() + "/data/" + PreferencesActivity.class.getPackage().getName() + "/shared_prefs/";
    // List of profile files available on the SD card
    private String[] profileFiles = null;
    // Which profile file to delete
    private int profileToDelete;

    // IDs of the default value from Resources - Resource Default ID (RDID)
    public static final int RDID_PROFILE = R.string.preferences_general_profile_default;
    public static final boolean RDID_FIRSTRUN = true;
    public static final int RDID_ENABLED = R.string.preferences_general_enabled_default;
    public static final int RDID_POSITION = R.string.preferences_general_position_default;
    public static final int RDID_UNKNOWN_ONLY = R.string.preferences_general_unknown_only_default;
    public static final int RDID_SHOW_COUNT = R.string.preferences_general_show_count_default;
    public static final int RDID_NUMBER_FILTER = R.string.preferences_general_number_filter_default;
    public static final int RDID_REPLACE = R.string.preferences_general_replace_default;
    public static final int RDID_FAKED_NUMBER = R.string.preferences_general_faked_number_default;
    public static final int RDID_SERVER = R.string.preferences_ldap_connection_server_default;
    public static final int RDID_PORT = R.string.preferences_ldap_connection_port_default;
    public static final int RDID_PROTOCOL = R.string.preferences_ldap_connection_protocol_default;
    public static final int RDID_BINDDN = R.string.preferences_ldap_connection_binddn_default;
    public static final int RDID_BASEDN = R.string.preferences_ldap_search_basedn_default;
    public static final int RDID_SEARCH_FILTER = R.string.preferences_ldap_search_filter_default;
    public static final int RDID_NAME_ATTRIBUTE = R.string.preferences_ldap_search_name_attribute_default;
    public static final int RDID_OTHER_ATTRIBUTES = R.string.preferences_ldap_search_other_attributes_default;
    public static final int RDID_SEPARATOR = R.string.preferences_ldap_search_separator_default;

    // Keys under which the value is saved into the file - File KEY (FKEY)
    public static final String FKEY_PROFILE = "profile";
    public static final String FKEY_FIRSTRUN = "first_run";
    public static final String FKEY_ENABLED = "enabled";
    public static final String FKEY_POSITION = "position";
    public static final String FKEY_UNKNOWN_ONLY = "unknown_only";
    public static final String FKEY_SHOW_COUNT = "show_count";
    public static final String FKEY_NUMBER_FILTER = "number_filter";
    public static final String FKEY_REPLACE = "replace";
    public static final String FKEY_FAKED_NUMBER = "faked_number";
    public static final String FKEY_SERVER = "server";
    public static final String FKEY_PORT = "port";
    public static final String FKEY_PROTOCOL = "protocol";
    public static final String FKEY_BINDDN = "binddn";
    public static final String FKEY_PASSWORD = "password";
    public static final String FKEY_BASEDN = "basedn";
    public static final String FKEY_SEARCH_FILTER = "search_filter";
    public static final String FKEY_NAME_ATTRIBUTE = "name_attribute";
    public static final String FKEY_OTHER_ATTRIBUTES = "other_attributes";
    public static final String FKEY_SEPARATOR = "separator";

    // Values of the protocol preferences (must correspond to the preferences_ldap_connection_protocol_values)
    public static final String PROTOCOL_NONE = "NONE";
    public static final String PROTOCOL_SSL = "SSL";
    public static final String PROTOCOL_TLS = "TLS";

    // Values of the position preferences (must correspond to the preferences_general_position_values)
    public static final String POSITION_TOP = "TOP";
    public static final String POSITION_MIDDLE = "MIDDLE";
    public static final String POSITION_BOTTOM = "BOTTOM";

    // IDs of dialogs in this Activity
    private final int DIALOG_WELCOME = 0;
    private final int DIALOG_ABOUT = 1;
    private final int DIALOG_TEMPLATE = 2;
    private final int DIALOG_DELETE_CONFIRM = 3;
    private final int DIALOG_CERN_TEMPLATE = 4;

    // IDs of templates (must correspond to the R.array.templates_values)
    private final int TEMPLATE_CERN = 0;

    // Menu items
    private static final int MENU_IMPORT = R.id.preferences_menu_import;
    private static final int MENU_EXPORT = R.id.preferences_menu_export;
    private static final int MENU_DELETE = R.id.preferences_menu_delete;
    private static final int MENU_TEMPLATE = R.id.preferences_menu_template;
    private static final int MENU_ABOUT = R.id.preferences_menu_about;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DEBUG > 0)
            Log.d(TAG, "Starting PreferencesActivity...");

        mContext = this;
        mPrefs = getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // During the first run
        if (! mPrefs.contains(PreferencesActivity.FKEY_FIRSTRUN)) {
            // Show the Welcome dialog
            showDialog(DIALOG_WELCOME);

            // Save that the next run is not the first one
            Editor editor = mPrefs.edit();
            editor.putBoolean(PreferencesActivity.FKEY_FIRSTRUN, false);
            editor.commit();
        }

        // Change the Activity title
        changeTitle();
    }

    @Override
    protected void onResume() {
        if (DEBUG > 0)
            Log.d(TAG, "onResume PreferencesActivity");

        super.onResume();

        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes.
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_WELCOME:
                return new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.welcome_dialog_title)
                .setMessage(R.string.welcome_dialog_message)
                .setPositiveButton(android.R.string.ok, null)
                .create();
            case DIALOG_ABOUT:
                LayoutInflater aboutFactory = LayoutInflater.from(mContext);
                final View aboutTextEntryView = aboutFactory.inflate(R.layout.about, null);
                return new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.about_dialog_title)
                .setView(aboutTextEntryView)
                .setPositiveButton(android.R.string.ok, null)
                .create();
            case DIALOG_TEMPLATE:
                return new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_menu_set_as)
                .setTitle(R.string.template_dialog_title)
                .setItems(R.array.templates_entries, loadTemplate)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
            case DIALOG_DELETE_CONFIRM:
                return new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_dialog_title))
                .setIcon(android.R.drawable.ic_menu_delete)
                .setMessage(R.string.delete_dialog_message)
                .setPositiveButton(android.R.string.yes, deleteProfileConfirm)
                .setNegativeButton(android.R.string.no, null)
                .show();
            case DIALOG_CERN_TEMPLATE:
                LayoutInflater CERNTemplateFactory = LayoutInflater.from(mContext);
                final View CERNTemplateTextEntryView = CERNTemplateFactory.inflate(R.layout.cern_dialog_login_entry, null);
                return new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_menu_set_as)
                .setTitle(R.string.CERN_template_dialog_title)
                .setView(CERNTemplateTextEntryView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Read the values
                        EditText username = (EditText) CERNTemplateTextEntryView.findViewById(R.id.CERN_username_edit);
                        EditText password = (EditText) CERNTemplateTextEntryView.findViewById(R.id.CERN_password_edit);

                        // Save the values
                        Editor editor = mPrefs.edit();

                        editor.putString(FKEY_BINDDN, mContext.getString(R.string.CERN_template_binddn).replace("your_nice_login", username.getText()));
                        editor.putString(FKEY_PASSWORD, password.getText().toString());

                        editor.commit();

                        // Reload preferences
                        reload();

                        Toast.makeText(mContext, R.string.CERN_template_dialog_ok, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        }

        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preferences, menu);

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_IMPORT:
                if (DEBUG > 0)
                    Log.d(TAG, "Import settings");

                // Get the content of the directory
                profileFiles = getProfileList();

                if (profileFiles != null && profileFiles.length > 0) {
                    // Show Import dialog
                    new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.import_dialog_title))
                    .setIcon(android.R.drawable.ic_menu_upload)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setItems(profileFiles, importProfile)
                    .show();
                } else {
                    Toast.makeText(this, R.string.import_no_profile_error, Toast.LENGTH_SHORT).show();
                }

                return true;
            case MENU_EXPORT:
                if (DEBUG > 0)
                    Log.d(TAG, "Export settings");

                // Export current profile
                exportProfile();

                return true;
            case MENU_DELETE:
                if (DEBUG > 0)
                    Log.d(TAG, "Delete settings");

                // Get the content of the directory
                profileFiles = getProfileList();

                if (profileFiles != null && profileFiles.length > 0) {
                    // Show the Delete dialog
                    new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_dialog_title))
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setItems(profileFiles, deleteProfile)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
                } else {
                    Toast.makeText(this, R.string.delete_no_profile_error, Toast.LENGTH_SHORT).show();
                }

                return true;
            case MENU_TEMPLATE:
                if (DEBUG > 0)
                    Log.d(TAG, "Load template");

                // Show Template dialog
                showDialog(DIALOG_TEMPLATE);

                return true;
            case MENU_ABOUT:
                if (DEBUG > 0)
                    Log.d(TAG, "About dialog");

                // Show the About dialog
                showDialog(DIALOG_ABOUT);

                return true;
        }

        return false;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG > 0)
            Log.d(TAG, "Prefs changed...");

        if (key.equals(FKEY_PROFILE)) {
            if (DEBUG > 1)
                Log.d(TAG, " - profile name changed");

            // Change the Activity title
            changeTitle();
        }
    }

    /**
     * Exports profile on the SD card.
     */
    private void exportProfile() {
        try {
            // Create the directory for the profiles
            new File(profilePath).mkdirs();

            // Copy shared preference file on the SD card
            copyFile(new File(sharedPrefsPath + PREFS_NAME + ".xml"), new File(profilePath + mPrefs.getString(FKEY_PROFILE, getString(RDID_PROFILE))));

            Toast.makeText(this, getString(R.string.export_ok), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_error), Toast.LENGTH_SHORT).show();
        }
    }

    private OnClickListener importProfile = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichItem) {
            try {
                copyFile(new File(profilePath + profileFiles[whichItem]), new File(sharedPrefsPath + PREFS_NAME + ".xml"));

                mPrefs = getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE);

                // Reload preferences
                reload();

                // Change the Activity title
                changeTitle();

                Toast.makeText(mContext, getString(R.string.import_ok),    Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(mContext, R.string.import_error,    Toast.LENGTH_SHORT).show();
                return;
            }
        }
    };

    private OnClickListener deleteProfile = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            // Remember which item to delete
            profileToDelete = whichButton;

            // Show the Delete Confirmation dialog
            showDialog(DIALOG_DELETE_CONFIRM);
        }
    };
            
    private OnClickListener deleteProfileConfirm = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            File profile = new File(profilePath + profileFiles[profileToDelete]);
            boolean rv = false;

            // Check if the file exists and try to delete it
            if (profile.exists()) {
                rv = profile.delete();
            }

            if (rv) {
                Toast.makeText(mContext, getString(R.string.delete_ok), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, getString(R.string.delete_error), Toast.LENGTH_SHORT).show();
            }
        }
    };

    private OnClickListener loadTemplate = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            if (DEBUG > 0)
                Log.d(TAG, "Loading template (" + whichButton + ")");

            switch (whichButton) {
                case TEMPLATE_CERN:
                    if (DEBUG > 0)
                        Log.d(TAG, " - template #" + whichButton);

                    // Open dialog with the NICE username and password input
                    showDialog(DIALOG_CERN_TEMPLATE);

                    String[] items = getResources().getStringArray(R.array.templates_entries);

                    Editor editor = mPrefs.edit();

                    editor.putString(FKEY_PROFILE, items[whichButton]);
                    editor.putString(FKEY_NUMBER_FILTER, getString(R.string.CERN_template_number_filter));
                    editor.putString(FKEY_REPLACE, getString(R.string.CERN_template_replace));
                    editor.putString(FKEY_SERVER, getString(R.string.CERN_template_server));
                    editor.putString(FKEY_PORT, getString(R.string.CERN_template_port));
                    editor.putString(FKEY_PROTOCOL, getString(R.string.CERN_template_protocol));
                    editor.putString(FKEY_BINDDN, getString(R.string.CERN_template_binddn));
                    editor.putString(FKEY_BASEDN, getString(R.string.CERN_template_basedn));
                    editor.putString(FKEY_SEARCH_FILTER, getString(R.string.CERN_template_search_filter));
                    editor.putString(FKEY_NAME_ATTRIBUTE, getString(R.string.CERN_template_name_attribute));
                    editor.putString(FKEY_OTHER_ATTRIBUTES, getString(R.string.CERN_template_other_attributes));

                    editor.commit();

                    // Reload preferences
                    reload();

                    // Change the Activity title
                    changeTitle();

                    break;
            }
        }
    };

    /**
     * Reloads the preferences. 
     */
    protected void reload() {
        setPreferenceScreen(null);
        addPreferencesFromResource(R.xml.preferences);
    }
    
    /**
     * Reads the list of profiles on the SD card.
     * 
     * @return Returns the list of profiles on the SD card.
     */
    public String[] getProfileList() {
        if (DEBUG > 0)
            Log.d(TAG, "Reading directory from SD card");

        // Get the list of files
        File dir = new File(profilePath);
        String[] files = dir.list();

        // Sort the list if some
        if (files != null) {
            Arrays.sort(files);
        }

        return files;
    }

    /**
     * Copy file from <code>in</code> to <code>out</code> file.
     * 
     * @param in
     *            Input file.
     * @param out
     *            Output file.
     * @throws Exception
     */
    protected void copyFile(File in, File out) throws Exception {
        FileInputStream  fis = new FileInputStream(in);
        FileOutputStream fos = new FileOutputStream(out);

        try {
            byte[] buf = new byte[1024];
            int i = 0;

            while ((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (fis != null) fis.close();
            if (fos != null) fos.close();
        }
    }

    /**
     * Change title of the preferences activity.
     */
    protected void changeTitle() {
        // Get the profile name
        String name = mPrefs.getString(FKEY_PROFILE, getString(RDID_PROFILE));

        // Set the title
        setTitle(getString(R.string.app_name) + " - " + name);
    }
}