package com.island.androidsftpdocumentsprovider.account;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff
 Copyright (C) 2020      Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import lu.knaff.alain.saf_sftp.R;

public class AuthenticationActivity extends Activity
{
	public static final String ACCOUNT_TYPE="com.island.sftp.account";
	public static final String AUTHORITY="com.island.androidsftpdocumentsprovider";

	private DBHandler dbHandler;
	private Account account = null;

	@Override
	protected void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		dbHandler = new DBHandler(this);
		setContentView(R.layout.authentication_activity);
		int accountId=getIntent().getIntExtra(DBHandler.ID_COL,-1);

		findViewById(R.id.add_account)
		    .setVisibility(accountId == -1 ? View.VISIBLE : View.GONE);
		findViewById(R.id.update_account)
		    .setVisibility(accountId != -1 ? View.VISIBLE : View.GONE);

		if(accountId != -1)
		{
			account=dbHandler.readAccountById(accountId);
			EditText host=findViewById(R.id.host);
			EditText port=findViewById(R.id.port);
			EditText user=findViewById(R.id.user);
			EditText directory=findViewById(R.id.start_directory);
			host.setText(account.getHostName());
			user.setText(account.getUserName());
			port.setText(String.valueOf(account.getPort()));
			directory.setText(String.valueOf(account.getDirectory()));
		}
	}

	private void cancel() {
		Intent result=new Intent();
		setResult(RESULT_CANCELED,result);
		finish();
	}

	public void confirm(View view) {
		String hostName=((EditText)findViewById(R.id.host))
			.getText().toString();

		String portString=((EditText)findViewById(R.id.port))
			.getText().toString();

		String userName=((EditText)findViewById(R.id.user))
			.getText().toString();

		String password=((EditText)findViewById(R.id.password))
			.getText().toString();
		String directory=((EditText)findViewById(R.id.start_directory))
			.getText().toString();

		if(hostName.isEmpty()||portString.isEmpty()||userName.isEmpty())
			return;
		int port = Integer.parseInt(portString);
		if(account != null) {
			// this is a request to edit an existing account

			// if nothing changed, exit
			if(hostName.equals(account.getHostName()) &&
			   userName.equals(account.getUserName()) &&
			   port == account.getPort() &&
			   password.isEmpty() &&
			   directory.equals(account.getDirectory())) {
				Toast.makeText(this,
					       R.string.nothing_changed,
					       Toast.LENGTH_SHORT)
					.show();
				return;
			}
		}

		String name = userName+"@"+hostName+":"+port;
		if(account == null) {
			dbHandler.addNewAccount(name, hostName, port,
						userName, password, directory);
			int flags=0;
			if(Build.VERSION.SDK_INT>=30)
			    flags |= ContentResolver.NOTIFY_INSERT;
			notifyChange(this, flags);
		} else {
			String oldName = account.getName();
			// update existing account
			if(password.isEmpty())
				password=account.getPassword();
			dbHandler.updateAccount(account.getId(),
						name, hostName, port,
						userName, password,
						directory);
			int flags=0;
			if(Build.VERSION.SDK_INT>=30)
			    flags |= ContentResolver.NOTIFY_UPDATE;
			notifyChange(this, flags);
		}

		Intent result=new Intent();
		setResult(RESULT_OK,result);
		finish();
	}

	public static void notifyChange(Context context, int mode) {
	    Uri uri = DocumentsContract.buildRootsUri(AUTHORITY);
	    context
		.getContentResolver()
		.notifyChange(uri, null, mode);
	}
}
