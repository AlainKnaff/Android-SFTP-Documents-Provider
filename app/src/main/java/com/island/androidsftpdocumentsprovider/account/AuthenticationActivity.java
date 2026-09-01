package com.island.androidsftpdocumentsprovider.account;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff
 Copyright (C) 2020      Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import java.util.List;
import java.io.IOException;
import java.net.ConnectException;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.island.sftp.SFTP;
import com.island.sftp.InteractiveUserInfo;
import com.island.androidsftpdocumentsprovider.provider.ProviderActivity;

import com.island.util.ErrorDialog;
import lu.knaff.alain.saf_sftp.R;

public class AuthenticationActivity extends ProviderActivity
{
	private Dao dao;
	private Account account = null;

	private final String TAG = "AuthenticationActivity";

	// id field name for intents
	public static final String ID_COL = "id";

	private static final int PROXY_TYPE_NONE = 0;
	private static final int PROXY_TYPE_SOCKS = 1;
	private static final int PROXY_TYPE_JUMP_HOST = 2;

	private int proxyType=0;
	private Integer jumpHostAccountId = null;

	@Override
	protected void onCreate(@Nullable Bundle icicle)
	{
		super.onCreate(icicle);
		dao = TheDatabase.getDao(getApplicationContext());
		setContentView(R.layout.authentication_activity);
		int accountId=getIntent()
			.getIntExtra(AuthenticationActivity.ID_COL,-1);

		findViewById(R.id.add_account)
		    .setVisibility(accountId == -1 ? View.VISIBLE : View.GONE);
		findViewById(R.id.add_account)
		    .setEnabled(false);
		findViewById(R.id.update_account)
		    .setVisibility(accountId != -1 ? View.VISIBLE : View.GONE);

		Spinner proxyTypeSpinner =
			(Spinner) findViewById(R.id.proxy_type);
		// Create an ArrayAdapter using the string array and a
		// default spinner layout.
		ArrayAdapter<CharSequence> adapter = ArrayAdapter
			.createFromResource(this,
					    R.array.proxy_type_array,
					    android.R.layout.simple_spinner_item);
		// Specify the layout to use when the list of choices appears.
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// Apply the adapter to the spinner.
		proxyTypeSpinner.setAdapter(adapter);
		proxyTypeSpinner.setOnItemSelectedListener(proxyTypeListener);

		List<Account> accounts = dao.getAllEligibleJumpHosts(accountId);
		Spinner jumpHostSpinner =
			(Spinner) findViewById(R.id.jump_host);
		// Create an ArrayAdapter using the string array and a
		// default spinner layout.
		ArrayAdapter<Account> jumpHostAdapter =
			new ArrayAdapter<>(this,
					   R.layout.spinner_item,
					   accounts);
		// Specify the layout to use when the list of choices appears.
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// Apply the adapter to the spinner.
		jumpHostSpinner.setAdapter(jumpHostAdapter);
		jumpHostSpinner.setOnItemSelectedListener(jumpHostListener);

		if(accountId != -1)
		{
			account=dao.readAccountById(accountId);
			EditText host=findViewById(R.id.host);
			EditText port=findViewById(R.id.port);
			EditText user=findViewById(R.id.user);
			EditText directory=findViewById(R.id.start_directory);
			EditText socksProxy=findViewById(R.id.socks_proxy);
			CheckBox hideFromList=findViewById(R.id.hide_from_list);

			host.setText(account.getHostName());
			user.setText(account.getUserName());
			port.setText(String.valueOf(account.getPort()));
			directory.setText(String.valueOf(account.getDirectory()));
			String socksProxyString = account.getSocksProxy();
			jumpHostAccountId = account.getJumpHostId();
			if(! "".equals(socksProxyString)) {
				proxyTypeSpinner.setSelection(PROXY_TYPE_SOCKS);
				socksProxy.setText(String.valueOf(socksProxyString));
			} else if(jumpHostAccountId != null) {
				proxyTypeSpinner.setSelection(PROXY_TYPE_JUMP_HOST);
			} else {
				proxyTypeSpinner.setSelection(PROXY_TYPE_NONE);
			}

			hideFromList.setChecked(account.getHideFromList());
		}
	}

	private void cancel() {
		Intent result=new Intent();
		setResult(RESULT_CANCELED,result);
		finish();
	}

	public void checkHostKey(@NonNull View view) {
		findViewById(R.id.check_host_key)
		    .setEnabled(false);

		String hostName=((EditText)findViewById(R.id.host))
			.getText().toString();

		String portString=((EditText)findViewById(R.id.port))
			.getText().toString();
		int port = Integer.parseInt(portString);

		String userName=((EditText)findViewById(R.id.user))
			.getText().toString();

		String password=((EditText)findViewById(R.id.password))
			.getText().toString();
		String directory=((EditText)findViewById(R.id.start_directory))
			.getText().toString();
		String socksProxy=((EditText)findViewById(R.id.socks_proxy))
			.getText().toString();

		Account account = new Account("test", hostName, port,
					      userName, password,
					      directory,
					      (proxyType==PROXY_TYPE_SOCKS) ? socksProxy : "",
					      (proxyType==PROXY_TYPE_JUMP_HOST) ? jumpHostAccountId : null,
					      false);

		new Thread(() -> {
			SFTP sftp=null;
			try {
				sftp = new SFTP(this, null, account,
						new InteractiveUserInfo(this));
			} catch(ConnectException e) {
				ErrorDialog.showError(this,
						      getString(R.string.test_connect_err),
						      e);
			}
			boolean success = sftp != null;
			if(sftp != null)
				try {
					sftp.close();
				} catch(IOException e) {
					// Problems during close can
					// safely be ignored
				}
			runOnUiThread(()->{
					findViewById(R.id.check_host_key)
						.setEnabled(true);
					if(success)
						findViewById(R.id.add_account)
							.setEnabled(true);
				});
		}).start();
	}

	private AdapterView.OnItemSelectedListener proxyTypeListener =
		new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent,
						    View view,
						    int position, long id) {
				proxyType = position;
				if(position == PROXY_TYPE_SOCKS)
					findViewById(R.id.socks_proxy)
						.setVisibility(View.VISIBLE);
				else
					findViewById(R.id.socks_proxy)
						.setVisibility(View.GONE);

				if(position == PROXY_TYPE_JUMP_HOST)
					findViewById(R.id.jump_host)
						.setVisibility(View.VISIBLE);
				else
					findViewById(R.id.jump_host)
						.setVisibility(View.GONE);

			}
			public void onNothingSelected(AdapterView<?> parent) {
			}
		};

	private AdapterView.OnItemSelectedListener jumpHostListener =
		new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent,
						   View view,
						   int position, long id) {
				Account acct= (Account)parent
					.getItemAtPosition(position);
				jumpHostAccountId=acct.getId();
			}
			public void onNothingSelected(AdapterView<?> parent) {
				jumpHostAccountId=null;
			}
		};

	public void confirm(@NonNull View view) {
		try {
			_confirm(view);
		} catch(Exception e) {
			ErrorDialog.showError(this, "Error saving account", e);
		}
	}

	private void _confirm(View view) {
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
		String socksProxy=((EditText)findViewById(R.id.socks_proxy))
			.getText().toString();
		boolean hideFromList = ((CheckBox)findViewById(R.id.hide_from_list))
			.isChecked();
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
			   directory.equals(account.getDirectory()) &&
			   (proxyType==PROXY_TYPE_SOCKS ? socksProxy : "").equals( account.getSocksProxy()) &&
			   (proxyType==PROXY_TYPE_JUMP_HOST ? jumpHostAccountId : null) ==  account.getJumpHostId() &&
			   hideFromList == account.getHideFromList()
			   ) {
				Toast.makeText(this,
					       R.string.nothing_changed,
					       Toast.LENGTH_SHORT)
					.show();
				return;
			}
		}

		String name = userName+"@"+hostName+":"+port;
		if(account == null) {
			dao.insertAll(new Account(name, hostName, port,
						  userName, password,
						  directory,
						  (proxyType==PROXY_TYPE_SOCKS)?socksProxy:"",
						  (proxyType==PROXY_TYPE_JUMP_HOST)?jumpHostAccountId:null,
						  hideFromList
						  ));
			int flags=0;
			if(Build.VERSION.SDK_INT>=30)
			    flags |= ContentResolver.NOTIFY_INSERT;
			notifyChange(flags);
		} else {
			String oldName = account.getName();
			// update existing account
			account.setName(name);
			account.setHostName(hostName);
			account.setPort(port);
			account.setUserName(userName);
			if(!password.isEmpty())
				account.setPassword(password);
			account.setDirectory(directory);
			account.setSocksProxy((proxyType==PROXY_TYPE_SOCKS)?socksProxy:"");
			if(proxyType==PROXY_TYPE_JUMP_HOST) {
				if(dao.isDescendant(jumpHostAccountId, account.getId()))
					throw new IllegalArgumentException("Would cause loop in jump hosts");
				if(jumpHostAccountId==account.getId())
					throw new IllegalArgumentException("Account cannot be jumphost for itself");
			}

			account.setJumpHostId((proxyType==PROXY_TYPE_JUMP_HOST)?jumpHostAccountId:null);
			account.setHideFromList(hideFromList);
			dao.update(account);
			int flags=0;
			if(Build.VERSION.SDK_INT>=30)
			    flags |= ContentResolver.NOTIFY_UPDATE;
			notifyChange(flags);
		}

		Intent result=new Intent();
		setResult(RESULT_OK,result);
		finish();
	}

}
