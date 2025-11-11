package com.island.androidsftpdocumentsprovider.account;
import android.app.Activity;
import android.accounts.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import com.island.androidsftpdocumentsprovider.*;
import com.island.androidsftpdocumentsprovider.provider.*;
public class AuthenticationActivity extends Activity
{
	public static final String ACCOUNT_TYPE="com.island.sftp.account";
	public static final String TOKEN_TYPE="login";
	public static final String AUTHORITY="com.island.androidsftpdocumentsprovider";
	@Override
	protected void onCreate(Bundle icicle)
	{
		Log.i(SFTPProvider.TAG,String.format("AuthenticationActivity onCreate %s",icicle));
		super.onCreate(icicle);
		if(getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)==null)
		{
			finish();
			return;
		}
		setContentView(R.layout.authentication_activity);
		Uri uri=getIntent().getData();
		if(uri!=null)
		{
			EditText ip=findViewById(R.id.ip);
			EditText port=findViewById(R.id.port);
			EditText user=findViewById(R.id.user);
			ip.setText(uri.getHost());
			user.setText(uri.getUserInfo());
			port.setText(String.valueOf(uri.getPort()));
		}
	}
	public void confirm(View view)
	{
		Log.i(SFTPProvider.TAG,String.format("AuthenticationActivity confirm %s",view));
		String ip=((EditText)findViewById(R.id.ip)).getText().toString();
		String port=((EditText)findViewById(R.id.port)).getText().toString();
		String user=((EditText)findViewById(R.id.user)).getText().toString();
		String password=((EditText)findViewById(R.id.password)).getText().toString();
		if(ip.isEmpty()||port.isEmpty()||user.isEmpty()||password.isEmpty())return;
		String accountType=getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
		String username=user+"@"+ip+":"+port;
		Account account=new Account(username,accountType);
		AccountManager accountManager=AccountManager.get(this);
		Bundle userdata=new Bundle();
		accountManager.addAccountExplicitly(account,password,userdata);
		ContentResolver.setSyncAutomatically(account,AUTHORITY,true);
		getContentResolver().notifyChange(DocumentsContract.buildRootsUri(AUTHORITY),null);
		Bundle data=new Bundle();
        data.putString(AccountManager.KEY_ACCOUNT_NAME,username);
        data.putString(AccountManager.KEY_ACCOUNT_TYPE,accountType);
        data.putString(AccountManager.KEY_AUTHTOKEN,password);
        Intent result=new Intent();
        result.putExtras(data);
        setResult(RESULT_OK,result);
        finish();
	}
}
