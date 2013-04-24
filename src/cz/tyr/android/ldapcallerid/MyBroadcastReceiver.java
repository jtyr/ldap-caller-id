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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * 
 * Broadcast Receiver which is activated when the Phone State changes.
 * 
 * @author Jiri Tyr
 *
 */
public class MyBroadcastReceiver extends BroadcastReceiver {
	private static final String TAG = "LDAPCallerID: MyBroadcastReceiver";
	private int DEBUG = 0;

	@Override
	public void onReceive(Context context, Intent intent) {
		if (DEBUG > 0)
			Log.d(TAG, "Inside the BroadcastReceiver...");

		// Check if the Phone State listener is already started
		if (CallerService.phoneStateListener == null) {
			if (DEBUG > 0)
				Log.d(TAG, " - starting new listener");

			// Start the Phone State listener
			CallerService.phoneStateListener = new MyPhoneStateListener(context);
			TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			telephony.listen(CallerService.phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		} else if (DEBUG > 0) {
			Log.d(TAG, " - listener already running!");
		}
	}
}