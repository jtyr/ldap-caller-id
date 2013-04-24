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

import android.util.Log;
import android.widget.Toast;

/**
 * 
 * Permanent toast created from standard <code>Toast</code> by repetitive
 * execution of the <code>show()</code> method.
 * 
 * @author Jiri Tyr
 * 
 */
public class PermanentToast extends Thread {
	private final static String TAG = "LDAPCallerID: PermanentToast";
	private int DEBUG = 0;
	private Toast mToast = null;

	/**
	 * Constructor of the <code>PermanentToast</code>.
	 * 
	 * @param toast
	 *            <code>Toast</code> to be shown permanently.
	 */
	public PermanentToast(Toast toast) {
		if (DEBUG > 0)
			Log.d(TAG, "Starting Toast thread...");

		mToast = toast;
	}

	@Override
	public void interrupt() {
		if (DEBUG > 0)
			Log.d(TAG, "Toast interuption");

		// Hide Toast
		mToast.cancel();

		try {
			super.interrupt();
		} catch (Exception e) {
			// Do nothing
		}
	}

	public void run() {
		try {
			// Keep the Toast alive until the thread runs  
			while (true) {
				mToast.show();
				sleep(1850);
			}
		} catch (Exception e) {
			if (DEBUG > 0)
				Log.d(TAG, "Thread interupted");
		}
	}
}