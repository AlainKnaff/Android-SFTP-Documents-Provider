package com.island.androidsftpdocumentsprovider

import android.accounts.AccountManager
import android.app.Activity
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

class MainActivity : Activity()
{
    private val TAG="MainActivity"
    override fun onCreate(savedInstanceState: Bundle?)
    {
        Log.i(TAG,"OnCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val recyclerView=findViewById<RecyclerView>(R.id.sftp_accounts)
        recyclerView.adapter=SFTPAdapter(this)
        recyclerView.layoutManager=LinearLayoutManager(this)
    }

    fun addSftpAccount(view:View)
    {
        Log.i(TAG,"AddSftpAccount $view")
        val accountManager:AccountManager= AccountManager.get(this)
        accountManager.addAccount(getString(R.string.account_type),null,null,null,this, null,null)
    }

    override fun onResume()
    {
        super.onResume()
        Log.i(TAG,"OnResume")
        (findViewById<RecyclerView>(R.id.sftp_accounts).adapter as SFTPAdapter).updateData()
    }

    class SFTPAdapter(private val activity:Activity):RecyclerView.Adapter<SFTPAdapter.ViewHolder>()
    {
        private val TAG="SFTPAdapter"
        private val accountManager:AccountManager= AccountManager.get(activity)
        private var accounts=accountManager.getAccountsByType(activity.getString(R.string.account_type))
        class ViewHolder(view:View):RecyclerView.ViewHolder(view)
        {
            val text:TextView = view.findViewById(R.id.text)
            val button:Button = view.findViewById(R.id.button)
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
            holder.button.setOnClickListener()
	    @SuppressLint("ImplicitSamInstance")
            @Suppress("deprecation")
            {
                accountManager.removeAccount(account, {updateData()},null)
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
            accounts=accountManager.getAccountsByType(activity.getString(R.string.account_type))
            notifyDataSetChanged()
        }
    }
}
