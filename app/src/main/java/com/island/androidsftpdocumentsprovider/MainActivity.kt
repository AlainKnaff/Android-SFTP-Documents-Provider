package com.island.androidsftpdocumentsprovider

import java.util.List

import android.app.Activity
import android.content.ContentResolver;
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.annotation.SuppressLint
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.island.androidsftpdocumentsprovider.account.Account
import com.island.androidsftpdocumentsprovider.account.DBHandler
import com.island.androidsftpdocumentsprovider.account.AuthenticationActivity

import com.island.sftp.Keygen

class MainActivity : Activity()
{
    private val TAG="MainActivity"

    private var dbHandler:DBHandler? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        Log.i(TAG,"OnCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        dbHandler = DBHandler(this)

        val recyclerView=findViewById<RecyclerView>(R.id.sftp_accounts)
        recyclerView.adapter=SFTPAdapter(this)
        recyclerView.layoutManager=LinearLayoutManager(this)
    }

    fun addSftpAccount(view:View)
    {
        Log.i(TAG,"AddSftpAccount $view")
	val intent:Intent = Intent(this, AuthenticationActivity::class.java)
	startActivity(intent)
    }

    fun editSftpAccount(view:View, account:Account?)
    {
        Log.i(TAG,"EditSftpAccount $view $account.id")
	val intent:Intent = Intent(this, AuthenticationActivity::class.java)
	intent.putExtra(DBHandler.ID_COL, account!!.id)
	startActivity(intent)
    }

    fun generateKey(view: View) {
	Keygen.genKey(this);
    }

    fun sharePublicKey(view: View) {
	Keygen.shareKey(this);
    }

    override fun onResume()
    {
        super.onResume()
        Log.i(TAG,"OnResume")
        (findViewById<RecyclerView>(R.id.sftp_accounts).adapter as SFTPAdapter).updateData()
    }

    inner class SFTPAdapter(private val activity:Activity):RecyclerView.Adapter<SFTPAdapter.ViewHolder>()
    {
        private val TAG="SFTPAdapter"
        private var accounts =dbHandler!!.readAccounts()
        inner class ViewHolder(view:View):RecyclerView.ViewHolder(view),
				    View.OnClickListener
        {
	    private val TAG="SFTPAdapter.ViewHolder"
            val text:TextView = view.findViewById(R.id.text)
            val button:Button = view.findViewById(R.id.button)
	    var account:Account? = null

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
            Log.i(TAG,"OnCreateViewHolder $parent $viewType")
            val view=LayoutInflater.from(parent.context).inflate(R.layout.sftp_item,parent,false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int)
        {
            Log.i(TAG,"OnBindViewHolder $holder $position")
            val account=accounts[position]
            holder.text.text=account.name
	    holder.account=account
            holder.button.setOnClickListener()
	    @SuppressLint("ImplicitSamInstance")
	    @Suppress("deprecation")
            {
		val oldName=account!!.name
		dbHandler!!.removeAccount(account!!.id)
		AuthenticationActivity
		    .notifyChange(this@MainActivity,
				  ContentResolver.NOTIFY_DELETE)
		updateData()
            }
        }

        override fun getItemCount(): Int
        {
            Log.i(TAG,"GetItemCount "+accounts.size)
            return accounts.size
        }

        fun updateData()
        {
            Log.i(TAG,"updateData")
            accounts=dbHandler!!.readAccounts()
            notifyDataSetChanged()
        }
    }
}
