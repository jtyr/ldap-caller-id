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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

/**
 * 
 * Service which is started from <code>MyBroadcastReceiver</code>.
 * 
 * @author Jiri Tyr
 *
 */
public class CallerService extends Service {
	private static final String TAG = "LDAPCallerID: CallerService";
	private static int DEBUG = 0;

	/**
	 * Reference to the Phone State listener.
	 */
	public static MyPhoneStateListener phoneStateListener = null;

	/**
	 * Receiver of messages form the <code>MyPhoneStateListener</code>.
	 */
	private BroadcastReceiver intentReceiver = null;

	/**
	 * Reference on the PermanentToast thread.
	 */
	private static Thread toastThread = null;
	/**
	 * Reference on the <code>LDAPSearch</code> thread.
	 */
	private static Thread ldapThread = null;

	@Override
	public void onCreate() {
		super.onCreate();

		if (DEBUG > 0)
			Log.d(TAG, "CallerService - onCreate");

		if (DEBUG > 0)
			Log.d(TAG, " - Creating new receiver");

		intentReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (DEBUG > 0)
					Log.d(TAG, " - CallerService - BroadcastReceiver - onReceive");

				Bundle extras = intent.getExtras();
				int callStatus = extras.getInt(MyPhoneStateListener.EXTRAS_CALL_STATUS);

				switch (callStatus) {
					case TelephonyManager.CALL_STATE_IDLE:
						if (DEBUG > 0)
							Log.d(TAG, "  * IDLE");

						// Cancel the currently running Toast 
						cancelToast();

						break;
					case TelephonyManager.CALL_STATE_OFFHOOK:
						if (DEBUG > 0)
							Log.d(TAG, "  * OFFHOOK");

						// Cancel the currently running Toast 
						cancelToast();

						break;
					default:
						if (DEBUG > 0)
							Log.d(TAG, "  * Some other call status: " + callStatus);

						break;
				}
			}
		};

		IntentFilter filter = new IntentFilter(MyPhoneStateListener.CALLERID_SHUTDOWN);
		registerReceiver(intentReceiver, filter);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

		if (DEBUG > 0)
			Log.d(TAG, "CallerService - onStart");

		// Sometimes NullPointerException can occur
		if (intent == null) {
			Log.e(TAG, " - No Intent defined!");
			return;
		}

		// Check if there is a data connection (WiFi, 3G, 4G)
		if (getNetworkStatus() == false) {
			if (DEBUG > 0)
				Log.d(TAG, " - No network connection!");

			return;
		}

		// Get the phone number from the intent
		Bundle extras = intent.getExtras();
		String incomingNumber = extras.getString(MyPhoneStateListener.EXTRAS_NUMBER); 

		if (DEBUG > 0)
			Log.d(TAG, " - Incoming phone number: " + incomingNumber);

		SharedPreferences prefs = getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE);

		// Fake the phone number
		String fakedNumber = prefs.getString(PreferencesActivity.FKEY_FAKED_NUMBER, (String) getText(PreferencesActivity.RDID_FAKED_NUMBER));
		if (fakedNumber.length() > 0) {
			incomingNumber = fakedNumber;

			if (DEBUG > 0)
				Log.d(TAG, " - Faking phone number: " + fakedNumber);
		}

		// Check if the Caller ID is enabled
		if (prefs.getBoolean(PreferencesActivity.FKEY_ENABLED, new Boolean((String) getText(PreferencesActivity.RDID_ENABLED))) == false) {
			if (DEBUG > 0)
				Log.d(TAG, " - LDAP Caller ID is disabled!");

			return;
		}

		// Check if there is some readable number
		if (incomingNumber.length() == 0) {
			if (DEBUG > 0)
				Log.e(TAG, " - Undefined phone number!");

			return;
		}

		// Compare the phone number with the filter
		if (incomingNumber.matches(prefs.getString(PreferencesActivity.FKEY_NUMBER_FILTER, (String) getText(PreferencesActivity.RDID_NUMBER_FILTER)))) {
			if (DEBUG > 0)
				Log.d(TAG, " - Filtered number");

			// Check if it is know number
			if (prefs.getBoolean(PreferencesActivity.FKEY_UNKNOWN_ONLY, new Boolean((String) getText(PreferencesActivity.RDID_UNKNOWN_ONLY)))) {
				Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incomingNumber));
				String[] projection = new String[]{ PhoneLookup.DISPLAY_NAME };
				Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

				try {
					if (cursor.getCount() > 0) {
						if (DEBUG > 0)
							Log.d(TAG, " - This is known number!");

						return;
					}
				} finally {
					cursor.close();
				}
			}

			// Modify the phone number if necessary
			String replaceString = prefs.getString(PreferencesActivity.FKEY_REPLACE, (String) getText(PreferencesActivity.RDID_REPLACE));
			if (replaceString.length() > 0) {
				String[] replacePairs = replaceString.split(";");
				for (int i=0; i<replacePairs.length; i++) {
					String[] replace = replacePairs[i].split(",");
					incomingNumber = incomingNumber.replaceAll(replace[0], replace[1]);
				}
			}

			if (DEBUG > 0)
				Log.d(TAG, " - Normalized number: " + incomingNumber);

			// Create custom Toast
			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View view = inflater.inflate(R.layout.details_searching, null);

			// Get the position of the toast
			String position = prefs.getString(PreferencesActivity.FKEY_POSITION, getString(PreferencesActivity.RDID_POSITION));

			Toast toast = new Toast(this);
			// Set the Toast position according to the preferences
			if (position.equals(PreferencesActivity.POSITION_TOP)) {
				toast.setGravity(Gravity.TOP, 0, 10);
			} else if (position.equals(PreferencesActivity.POSITION_MIDDLE)) {
					toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 10);
			} else {
				toast.setGravity(Gravity.BOTTOM, 0, 10);
			}
			toast.setDuration(Toast.LENGTH_LONG);
			toast.setView(view);
			toast.show();

			// Keep the Toast alive in a thread
			toastThread = new PermanentToast(toast);
			toastThread.start();

			// Start searching the LDAP directory
			ldapThread = new LDAPSearch(this, toast, incomingNumber);
			ldapThread.start();

			if (DEBUG > 0)
				Log.d(TAG, " - Toast should be shown!");
		} else if (DEBUG > 0) {
			Log.d(TAG, " - Foreign number");
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (DEBUG > 0)
			Log.d(TAG, "CallerService - onBind");

		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (DEBUG > 0)
			Log.d(TAG, "CallerService - onDestroy");
	}

	/**
	 * Check the network status.
	 * 
	 * @return Returns <code>true</code> if there is a network connection and
	 *         <code>false</code> in the opposite case.
	 */
	private boolean getNetworkStatus() {
		if (DEBUG > 0)
			Log.d(TAG, " - Searching for data connection");

		boolean hasNetwork = false;

		ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo inf[] = connectivity.getAllNetworkInfo();

		for (int i = 0; i < inf.length; i++) {
			if (inf[i].getType() == ConnectivityManager.TYPE_MOBILE || inf[i].getType() == ConnectivityManager.TYPE_WIFI) {
				if (DEBUG > 0)
					Log.d(TAG, "    * Has \"" + inf[i].getTypeName() + "\" network? " + inf[i].isConnected());

				if (inf[i].isConnected()) {
					hasNetwork = true;

					break;
				}
			}
		}

		return hasNetwork;
	}

	/**
	 * Close the thread with the <code>PermanentToast</code> and the
	 * <code>LDAPSearch</code>.
	 */
	public static void cancelToast() {
		if (DEBUG > 0)
			Log.d(TAG, "Cancel Toast");

		// Stop LDAP thread
		if (ldapThread != null && ldapThread.isAlive()) {
			if (DEBUG > 0)
				Log.d(TAG, " - Stopping LDAP thread");

			ldapThread.interrupt();
		}

		// Stop Toast thread
		if (toastThread != null && toastThread.isAlive()) {
			if (DEBUG > 0)
				Log.d(TAG, " - Stopping Toast thread");

			toastThread.interrupt();
		}
	}
}