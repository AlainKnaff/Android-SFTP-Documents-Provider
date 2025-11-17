package com.island.androidsftpdocumentsprovider.account;

import java.io.IOException;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.island.sftp.SFTP;
import com.island.androidsftpdocumentsprovider.R;
import com.island.androidsftpdocumentsprovider.provider.SFTPProvider;

public class AuthenticationActivity extends Activity
{
	public static final String ACCOUNT_TYPE="com.island.sftp.account";
	public static final String TOKEN_TYPE="login";
	public static final String AUTHORITY="com.island.androidsftpdocumentsprovider";

	private DBHandler dbHandler;
	private Account account = null;

	@Override
	protected void onCreate(Bundle icicle)
	{
		Log.i(SFTPProvider.TAG,String.format("AuthenticationActivity onCreate %s",icicle));
		super.onCreate(icicle);
		dbHandler = new DBHandler(this);
		setContentView(R.layout.authentication_activity);
		int accountId=getIntent().getIntExtra(DBHandler.ID_COL,-1);
		if(accountId != -1)
		{
			account=dbHandler.readAccountById(accountId);
			EditText host=findViewById(R.id.host);
			EditText port=findViewById(R.id.port);
			EditText user=findViewById(R.id.user);
			host.setText(account.getHostName());
			user.setText(account.getUserName());
			port.setText(String.valueOf(account.getPort()));
		}
	}

	private void cancel() {
		Intent result=new Intent();
		setResult(RESULT_CANCELED,result);
		finish();
	}

	public void confirm(View view) {
		Log.i(SFTPProvider.TAG,String.format("AuthenticationActivity confirm %s",view));
		String hostName=((EditText)findViewById(R.id.host))
			.getText().toString();

		String portString=((EditText)findViewById(R.id.port))
			.getText().toString();

		String userName=((EditText)findViewById(R.id.user))
			.getText().toString();

		String password=((EditText)findViewById(R.id.password))
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
			   password.isEmpty()) {
				Log.i(SFTPProvider.TAG,"Nothing changed");
				cancel();
				return;
			}
		}

		String name = userName+"@"+hostName+":"+port;
		if(account == null) {
			// new account creation
			if(password.isEmpty())
				return;
			dbHandler.addNewAccount(name, hostName, port,
						userName, password);
		} else {
			// update existing account
			if(password.isEmpty())
				password=account.getPassword();
			dbHandler.updateAccount(account.getId(),
						name, hostName, port,
						userName, password);
		}

		Intent result=new Intent();
		setResult(RESULT_OK,result);
		finish();
	}
}
