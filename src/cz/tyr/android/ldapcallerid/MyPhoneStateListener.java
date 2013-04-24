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

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * 
 * Phone State listener created in the <code>CallerService</code>.
 * 
 * @author Jiri Tyr
 *
 */
public class MyPhoneStateListener extends PhoneStateListener {
	private static final String TAG = "LDAPCallerID: MyPhoneStateListener";
	private int DEBUG = 0;
	private static Context mContext = null;

	// Runnable which send a messages to the CallerService
	private Runnable mOffhookShutdownMessageTask = null;
	private Runnable mIdleShutdownMessageTask = null;

	/**
	 * Label for number Extras parameter.
	 */
	public static final String EXTRAS_NUMBER = "number";
	/**
	 * Label for call status Extras parameter.
	 */
	public static final String EXTRAS_CALL_STATUS = "call_status";

	/**
	 * Intent action.
	 */
	public static final String CALLERID_SHUTDOWN = MyPhoneStateListener.class.getPackage().getName() + "CALLERID_SHUTDOWN";

	/**
	 * Constructor which declares the <code>mIdleShutdownMessageTask</code> and
	 * <code>mOffhookShutdownMessageTask</code> threads.
	 * 
	 * @param context
	 *            Application context.
	 */
	public MyPhoneStateListener(Context context) {
		mContext = context;

		mIdleShutdownMessageTask = new Runnable() {
			public void run() {
				MyPhoneStateListener.sendIdleShutdownMessage();
			}
		};

		mOffhookShutdownMessageTask = new Runnable() {
			public void run() {
				MyPhoneStateListener.sendOffhookShutdownMessage();
			}
		};
	}

	public void onCallStateChanged(int state, String incomingNumber){
		if (DEBUG > 0)
			Log.d(TAG, "Inside the PhoneStateListener...");

		switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
				if (DEBUG > 0)
					Log.d(TAG, " - RINGING");

				// Start the service which shows the PermanentToast
				Intent myIntent = new Intent(mContext, CallerService.class);
				myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				myIntent.putExtra(EXTRAS_NUMBER, incomingNumber);
				mContext.startService(myIntent);

				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				if (DEBUG > 0)
					Log.d(TAG, " - OFFHOOK");

				Handler handleOffHook = new Handler();
				handleOffHook.post(mOffhookShutdownMessageTask);

				break;
			case TelephonyManager.CALL_STATE_IDLE:
				if (DEBUG > 0)
					Log.d(TAG, " - IDLE");

				Handler handleIdle = new Handler();
				handleIdle.post(mIdleShutdownMessageTask);

				break;
			default:
				if (DEBUG > 0)
					Log.d(TAG, " - Other state: " + state);

				break;
		}
	}

	/**
	 * Send the <code>CALLERID_SHUTDOWN</code> message when phone status changes
	 * to Idle.
	 */
	public static void sendIdleShutdownMessage() {
		Intent intent = new Intent(CALLERID_SHUTDOWN);
		intent.putExtra(EXTRAS_CALL_STATUS, TelephonyManager.CALL_STATE_IDLE);

		mContext.sendBroadcast(intent);
	}

	/**
	 * Send the <code>CALLERID_SHUTDOWN</code> message when phone status changes
	 * to Offhook.
	 */
	public static void sendOffhookShutdownMessage() {
		Intent intent = new Intent(CALLERID_SHUTDOWN);
		intent.putExtra(EXTRAS_CALL_STATUS, TelephonyManager.CALL_STATE_OFFHOOK);

		mContext.sendBroadcast(intent);
	}
}