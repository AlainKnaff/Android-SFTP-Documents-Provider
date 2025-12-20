package com.island.androidsftpdocumentsprovider

import android.net.Uri;
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Intent
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.annotation.SuppressLint
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.island.androidsftpdocumentsprovider.account.Account
import com.island.androidsftpdocumentsprovider.account.DBHandler
import com.island.androidsftpdocumentsprovider.account.AuthenticationActivity

import com.island.sftp.Keygen

class MainActivity : Activity()
{
    private val TAG="MainActivity"

    private val dbHandler: DBHandler by lazy { DBHandler(this) }

    private fun fixButtonState() {
	val share: Button  = findViewById(R.id.share_public_key)
	val generate: Button  = findViewById(R.id.generate_keypair)
	if(Keygen.haveKey(this)) {
	    share.setEnabled(true);
	    generate.setText(R.string.regenerate_key)
	} else {
	    share.setEnabled(false);
	}
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
	super.onCreate(savedInstanceState)
	setContentView(R.layout.main)

	val recyclerView=findViewById<RecyclerView>(R.id.sftp_accounts)
	recyclerView.adapter=SFTPAdapter(this)
	recyclerView.layoutManager=LinearLayoutManager(this)
	fixButtonState();
    }

    fun browseFiles(view:View)
    {
	val intent = Intent(Intent.ACTION_VIEW,null)
	intent.setComponent(ComponentName("com.google.android.documentsui",
					  "com.android.documentsui.files.FilesActivity"));
	intent.setData(Uri.parse("content://com.island.androidsftpdocumentsprovider/root"))
	startActivity(intent)
    }

    fun addSftpAccount(view:View)
    {
	val intent:Intent = Intent(this, AuthenticationActivity::class.java)
	startActivity(intent)
    }

    fun editSftpAccount(view:View, account:Account)
    {
	val intent:Intent = Intent(this, AuthenticationActivity::class.java)
	intent.putExtra(DBHandler.ID_COL, account.id)
	startActivity(intent)
    }

    fun generateKey(view: View) {
	if(Keygen.haveKey(this)) {
	    // pop up a dialog
	    val builder: AlertDialog.Builder = AlertDialog.Builder(this)
	    builder
		.setMessage(R.string.keygen_confirm)
		.setPositiveButton(R.string.yes) { d, w -> generateKey2(view) }
		.setNegativeButton(R.string.no) { d, w  ->d.dismiss() }
		.show();
	} else {
	    generateKey2(view)
	}
    }

    private fun generateKey2(view: View) {
	Keygen.genKey(this);
	fixButtonState();
    }

    fun sharePublicKey(view: View) {
	Keygen.shareKey(this);
    }

    override fun onResume()
    {
	super.onResume()
	(findViewById<RecyclerView>(R.id.sftp_accounts).adapter as SFTPAdapter).updateData()
    }

    inner class SFTPAdapter(private val activity:Activity):RecyclerView.Adapter<SFTPAdapter.ViewHolder>()
    {
	private val TAG="SFTPAdapter"
	private var accounts =dbHandler.readAccounts()
	inner class ViewHolder(view:View):RecyclerView.ViewHolder(view),
					  View.OnClickListener
	{
	    private val TAG="SFTPAdapter.ViewHolder"
	    val text:TextView = view.findViewById(R.id.text)
	    val button:Button = view.findViewById(R.id.button)
	    public lateinit var account:Account

	    init {
		view.setOnClickListener(this)
		text.setOnClickListener(this)
	    }

	    override fun onClick(view: View)
	    {
		editSftpAccount(view,account)
	    }
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder
	{
	    val view=LayoutInflater.from(parent.context).inflate(R.layout.sftp_item,parent,false)
	    return ViewHolder(view)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int)
	{
	    val account=accounts[position]
	    holder.text.text=account.name
	    holder.account=account
	    holder.button.setOnClickListener()
	    @SuppressLint("ImplicitSamInstance")
	    @Suppress("deprecation")
	    {
		val oldName=account.name
		dbHandler.removeAccount(account.id)
		var flags=0
		if(Build.VERSION.SDK_INT>=30)
		    flags = flags or ContentResolver.NOTIFY_DELETE
		AuthenticationActivity
		    .notifyChange(this@MainActivity, flags)
		updateData()
	    }
	}

	override fun getItemCount(): Int
	{
	    return accounts.size
	}

	fun updateData()
	{
	    accounts=dbHandler.readAccounts()
	    @SuppressLint("NotifyDataSetChanged")
	    // not a huge list, and sometimes we cannot indeed
	    // describe which position has changed exactly, such as
	    // when *adding* a new item
	    notifyDataSetChanged()
	}
    }
}
