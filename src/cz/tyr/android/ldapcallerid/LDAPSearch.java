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

import java.security.GeneralSecurityException;

import javax.net.SocketFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;

/**
 * 
 * Thread which executes the search on the LDAP server. 
 * 
 * @author Jiri Tyr
 *
 */
public class LDAPSearch extends Thread {
	private final static String TAG = "LDAPCallerID: LDAPSearch"; 
	private int DEBUG = 0;

	// LDAP connection handler
	private LDAPConnection conn = null;
	// Reference to the parent Activity
	private CallerService mService = null;
	// Reference to the Toast
	private Toast mToast = null;
	// Number to search
	private String mNumber = null;
	// Thread used to timeout the LDAP connection
	private Thread timeoutThread = null;
	// Reference to the Shared Preferences
	private SharedPreferences mPrefs = null;

	// Maximum number of records selected from the LDAP directory
	private final int SIZE_LIMIT = 15;
	// LDAP connection timeout (30sec)
	private final int TIMEOUT = 30*1000;

	/**
	 * Constructor bringing necessary parameters.
	 * 
	 * @param callerService
	 *            Reference to the <code>CallerService</code>.
	 * @param toast
	 *            Reference to the <code>Toast</code>.
	 * @param number
	 *            Phone number.
	 */
	public LDAPSearch(CallerService callerService, Toast toast, String number) {
		if (DEBUG > 0)
			Log.d(TAG, "Starting LDAP thread");

		mService = callerService;
		mToast = toast;
		mNumber = number;
		mPrefs = mService.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE);
	}

	@Override
	public void interrupt() {
		if (DEBUG > 0)
			Log.d(TAG, "LDAP interuption");

		// Close connection
		if (conn != null) {
			conn.close();
		}

		// Stop timeout thread
		if (timeoutThread != null) {
			timeoutThread.interrupt();
		}

		super.interrupt();
	}

	public void run() {
		// Establish connection
		conn = establishConnection();
		if (conn == null) {
			showError(mService.getString(R.string.details_connection_error));
			return;
		}

		// Get search results
		SearchResult result = getSearchResult(conn);
		if (result == null) {
			showError(mService.getString(R.string.details_result_error));
			return;
		}

		if (result.getResultCode() == ResultCode.SUCCESS) {
			if (result.getEntryCount() == 0) {
				if (DEBUG > 0)
					Log.d(TAG, "Unknown number!");

				showMessage(mService.getString(R.string.details_unknown_number));
			} else {
				LayoutInflater inflater = (LayoutInflater) mService.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

				// Take the first item from the list
				SearchResultEntry entries = result.getSearchEntries().get(0);

				// Compose the caller name string
				String callerName = entries.getAttributeValue(mPrefs.getString(PreferencesActivity.FKEY_NAME_ATTRIBUTE, (String) mService.getText(PreferencesActivity.RDID_NAME_ATTRIBUTE)));
				// If there is more entries and we want to show the number of entries, add it to the caller name
				if (result.getEntryCount() > 1 && mPrefs.getBoolean(PreferencesActivity.FKEY_SHOW_COUNT, new Boolean(mService.getString(PreferencesActivity.RDID_SHOW_COUNT)))) {
					callerName += " (" + result.getEntryCount() + ")";
				}

				// Compose the "others" string
				String separator = mPrefs.getString(PreferencesActivity.FKEY_SEPARATOR, (String) mService.getText(PreferencesActivity.RDID_SEPARATOR)).replaceAll("\\\\n", "\\\n");
				String[] otherAttributes = mPrefs.getString(PreferencesActivity.FKEY_OTHER_ATTRIBUTES, (String) mService.getText(PreferencesActivity.RDID_OTHER_ATTRIBUTES)).split(",");
				String otherString = "";
				for (int i=0; i<otherAttributes.length; i++) {
					// Avoid null string in the output
					String value = entries.getAttributeValue(otherAttributes[i]);
					if (value != null) {
						otherString += value;
						if (i+1 < otherAttributes.length) {
							otherString += separator;
						}
					}
				}

				// Create custom layout for the Toast
				View view = inflater.inflate(R.layout.details, null);

				TextView nameTV = (TextView) view.findViewById(R.id.name);
				nameTV.setText(callerName);
				// If there is more entries, make the name red
				if (result.getEntryCount() > 1) {
					nameTV.setTextColor(Color.RED);
				}
				TextView othersTV = (TextView) view.findViewById(R.id.others);
				othersTV.setText(otherString);

				mToast.setView(view);
			}
		} else {
			if (DEBUG > 0)
				Log.d(TAG, "ResultCode: (" + result.getResultCode() + ")" + result.getResultCode().getName());

			showError(mService.getString(R.string.details_result_error_number, result.getResultCode()));
			return;
		}
	}

	/**
	 * Creates LDAP connection.
	 * 
	 * @return Returns <code>LDAPConnection</code> object.
	 */
	private LDAPConnection establishConnection() {
		// By default we use non-SSL connection
		SocketFactory socketFactory = SocketFactory.getDefault();

		// Try to create SSL socket if required
		if (mPrefs.getString(PreferencesActivity.FKEY_PROTOCOL, (String) mService.getText(PreferencesActivity.RDID_PROTOCOL)).equals(PreferencesActivity.PROTOCOL_SSL)) {
			final SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());

			try {
				socketFactory = sslUtil.createSSLSocketFactory();
			} catch (GeneralSecurityException e) {
				if (DEBUG > 0)
					Log.e(TAG, "Can not create SSL socket: " + e.getMessage());
				return null;
			}
		}

		// Set up the connection options
		final LDAPConnectionOptions options = new LDAPConnectionOptions();
		options.setAutoReconnect(true);
		options.setConnectTimeoutMillis(TIMEOUT);
		options.setFollowReferrals(false);
		options.setMaxMessageSize(1024*1024);

		// Read values from preferences
		String server = mPrefs.getString(PreferencesActivity.FKEY_SERVER, (String) mService.getText(PreferencesActivity.RDID_SERVER));
		int port = 0;
		try {
			port = new Integer(mPrefs.getString(PreferencesActivity.FKEY_PORT, (String) mService.getText(PreferencesActivity.RDID_PORT)));
		} catch (NumberFormatException e) {
			Log.e(TAG, "Can not convert the port string into the number!");
		}
		String binddn = mPrefs.getString(PreferencesActivity.FKEY_BINDDN, (String) mService.getText(PreferencesActivity.RDID_BINDDN));
		String password = mPrefs.getString(PreferencesActivity.FKEY_PASSWORD, "");

		if (DEBUG > 0) {
			Log.d(TAG, " * Connecting to: " + server + ":" + port);
			Log.d(TAG, " * BindDN: " + PreferencesActivity.FKEY_BINDDN + "=" + binddn);
		}

		// Try to establish the LDAP connection
		LDAPConnection connection = null;
		try {
			connection = new LDAPConnection(socketFactory, options, server, port);
			connection.bind(binddn, password);
		} catch (LDAPException e) {
			if (DEBUG > 0)
				Log.e(TAG, "Can not established LDAP connection: " + e.getExceptionMessage());
			return null;
		}

		if (DEBUG > 0)
			Log.d(TAG, "LDAP connection established");

		return connection;
	}

	/**
	 * Executes the LDAP search.
	 * 
	 * @param connection
	 *            Reference to the LDAP connection.
	 * @return Returns result of the search.
	 */
	private SearchResult getSearchResult(LDAPConnection connection) {
		// Join the "name" and "others" attributes into one array
		String[] otherAttributes = mPrefs.getString(PreferencesActivity.FKEY_OTHER_ATTRIBUTES, (String) mService.getText(PreferencesActivity.RDID_OTHER_ATTRIBUTES)).split(","); 
		final String[] attributesToReturn = new String[otherAttributes.length + 1];
		attributesToReturn[0] = mPrefs.getString(PreferencesActivity.FKEY_NAME_ATTRIBUTE, (String) mService.getText(PreferencesActivity.RDID_NAME_ATTRIBUTE)); 
		for (int i=1; i<attributesToReturn.length; i++) {
			attributesToReturn[i] = otherAttributes[i-1];
		}

		final String basedn = mPrefs.getString(PreferencesActivity.FKEY_BASEDN, (String) mService.getText(PreferencesActivity.RDID_BASEDN));
		final String filter_string = mPrefs.getString(PreferencesActivity.FKEY_SEARCH_FILTER, (String) mService.getText(PreferencesActivity.RDID_SEARCH_FILTER));

		if (DEBUG > 0) {
			Log.d(TAG, " * BaseDN: " + basedn);
			Log.d(TAG, " * Filter: " + filter_string);
		}

		// Create search filter
		Filter filter = null;
		try {
			filter = Filter.create(filter_string.replaceAll("%n", mNumber));
		} catch (LDAPException e1) {
			if (DEBUG > 0)
				Log.e(TAG, "Can not create filter: " + e1.getExceptionMessage());

			return null;
		}

		// If we need to follow referrals
		//BooleanArgument followReferrals = null;
		//try {
		//	followReferrals = new BooleanArgument('R', "followReferrals", "Some description");
		//} catch (ArgumentException e2) {
		//	Log.d(TAG, "Can not create BooleanArgument: " + e2.getExceptionMessage());
		//}

		final SearchRequest searchRequest = new SearchRequest(basedn, SearchScope.SUB, filter);
		//searchRequest.setFollowReferrals(followReferrals.isPresent());
		searchRequest.setSizeLimit(SIZE_LIMIT);
		searchRequest.setAttributes(attributesToReturn);

		// Get the result
		SearchResult result = null;
		try {
			result = connection.search(searchRequest);
			if (DEBUG > 0) {
				Log.d(TAG, "The search operation was processed successfully.");
				Log.d(TAG, "Entries returned:  " + result.getEntryCount());
				Log.d(TAG, "References returned:  " + result.getReferenceCount());
			}
		} catch (LDAPException le) {
			Log.e(TAG, "Can not get search result: " + le.getExceptionMessage());
		}

		return result;
	}

	/**
	 * Show error message in the <code>Toast</code>.
	 * 
	 * @param message
	 *            Text to be shown.
	 */
	private void showError(String message) {
		showMessage(message);

		// Start thread which will cancel the Toast after 3 seconds
		timeoutThread = new Thread() {
			public void run() {
				try {
					sleep(3000);
					CallerService.cancelToast();
				} catch (InterruptedException e) {
					if (DEBUG > 0)
						Log.d(TAG, "Thread interupted");
				}
			}
		};
		timeoutThread.start();
	}

	/**
	 * Show message in the <code>Toast</code>.
	 * 
	 * @param message
	 *            Text to be shown.
	 */
	private void showMessage(String message) {
		LayoutInflater inflater = (LayoutInflater) mService.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		// Create custom layout the Toast
		View view = inflater.inflate(R.layout.details_message, null);

		TextView messageTV = (TextView) view.findViewById(R.id.message);
		messageTV.setText(message);

		mToast.setView(view);
	}
}