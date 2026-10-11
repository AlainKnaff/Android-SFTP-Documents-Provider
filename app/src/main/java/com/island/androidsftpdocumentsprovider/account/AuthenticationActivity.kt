package com.island.androidsftpdocumentsprovider.account

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.island.androidsftpdocumentsprovider.account.TheDatabase.Companion.getDao
import com.island.androidsftpdocumentsprovider.provider.ProviderActivity
import com.island.sftp.InteractiveUserInfo
import com.island.sftp.SFTP
import com.island.util.ErrorDialog.Companion.showError
import lu.knaff.alain.saf_sftp.R
import java.io.IOException
import java.net.ConnectException

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
Copyright (C) 2025,2026 Alain Knaff
Copyright (C) 2020      Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

class AuthenticationActivity : ProviderActivity() {
    private var dao: Dao? = null
    private var account: Account? = null

    private var proxyType = 0
    private var jumpHostAccountId: Int? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        dao = getDao(applicationContext)
        setContentView(R.layout.authentication_activity)
        val accountId = intent
            .getIntExtra(ID_COL, -1)

        findViewById<View>(R.id.add_account).visibility = if (accountId == -1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.add_account).isEnabled = false
        findViewById<View>(R.id.update_account).visibility = if (accountId != -1) View.VISIBLE else View.GONE

        val proxyTypeSpinner = findViewById<Spinner>(R.id.proxy_type)
        // Create an ArrayAdapter using the string array and a
        // default spinner layout.
        val adapter = ArrayAdapter
            .createFromResource(
                this,
                R.array.proxy_type_array,
                android.R.layout.simple_spinner_item
            )
        // Specify the layout to use when the list of choices appears.
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner.
        proxyTypeSpinner.adapter = adapter
        proxyTypeSpinner.onItemSelectedListener = proxyTypeListener

        val accounts: MutableList<Account> = dao!!.getAllEligibleJumpHosts(accountId)
        val jumpHostSpinner = findViewById<Spinner>(R.id.jump_host)
        // Create an ArrayAdapter using the string array and a
        // default spinner layout.
        val jumpHostAdapter =
            ArrayAdapter<Account?>(
                this,
                R.layout.spinner_item,
                accounts
            )
        // Specify the layout to use when the list of choices appears.
        jumpHostAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner.
        jumpHostSpinner.adapter = jumpHostAdapter
        jumpHostSpinner.onItemSelectedListener = jumpHostListener

        if (accountId != -1) {
            account = dao!!.readAccountById(accountId)
            val displayName = findViewById<EditText>(R.id.displayName)

            val host = findViewById<EditText>(R.id.host)
            val port = findViewById<EditText>(R.id.port)
            val user = findViewById<EditText>(R.id.user)
            val directory = findViewById<EditText>(R.id.start_directory)
            val socksProxy = findViewById<EditText>(R.id.socks_proxy)
            val hideFromList = findViewById<CheckBox>(R.id.hide_from_list)

            displayName.setText(account!!.displayName)
            host.setText(account!!.hostName)
            user.setText(account!!.userName)
            @SuppressLint("SetTextI18n")
            port.setText(account!!.port.toString())
            directory.setText(account!!.directory)
            val socksProxyString = account!!.socksProxy
            jumpHostAccountId = account!!.jumpHostId
            if ("" != socksProxyString) {
                proxyTypeSpinner.setSelection(PROXY_TYPE_SOCKS)
                socksProxy.setText(socksProxyString)
            } else if (jumpHostAccountId != null) {
                proxyTypeSpinner.setSelection(PROXY_TYPE_JUMP_HOST)
            } else {
                proxyTypeSpinner.setSelection(PROXY_TYPE_NONE)
            }

            hideFromList.isChecked = account!!.hideFromList
        }
    }

    fun checkHostKey(ignoredView: View) {
        findViewById<View>(R.id.check_host_key).isEnabled = false

        val displayName = (findViewById<View?>(R.id.displayName) as EditText)
            .text.toString()

        val hostName = (findViewById<View?>(R.id.host) as EditText)
            .text.toString()

        val portString = (findViewById<View?>(R.id.port) as EditText)
            .text.toString()
        val port = portString.toInt()

        val userName = (findViewById<View?>(R.id.user) as EditText)
            .text.toString()

        val password = (findViewById<View?>(R.id.password) as EditText)
            .text.toString()
        val directory = (findViewById<View?>(R.id.start_directory) as EditText)
            .text.toString()
        val socksProxy = (findViewById<View?>(R.id.socks_proxy) as EditText)
            .text.toString()

        val account = Account(
            "test",
            displayName,
            hostName, port,
            userName, password,
            directory,
            if (proxyType == PROXY_TYPE_SOCKS) socksProxy else "",
            if (proxyType == PROXY_TYPE_JUMP_HOST) jumpHostAccountId else null,
            false
        )

        Thread {
            var sftp: SFTP? = null
            try {
                sftp = SFTP(
                    this, "dummy", account,
                    InteractiveUserInfo(this)
                )
            } catch (e: ConnectException) {
                showError(
                    this,
                    getString(R.string.test_connect_err),
                    e
                )
            }
            val success = sftp != null
            if (sftp != null) try {
                sftp.close()
            } catch (_: IOException) {
                // Problems during close can
                // safely be ignored
            }
            runOnUiThread {
                findViewById<View>(R.id.check_host_key).isEnabled = true
                if (success) findViewById<View>(R.id.add_account).isEnabled = true
            }
        }.start()
    }

    private val proxyTypeListener: AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int, id: Long
            ) {
                proxyType = position
                if (position == PROXY_TYPE_SOCKS) findViewById<View>(R.id.socks_proxy).visibility =
                    View.VISIBLE
                else findViewById<View>(R.id.socks_proxy).visibility = View.INVISIBLE

                if (position == PROXY_TYPE_JUMP_HOST) findViewById<View>(R.id.jump_host).visibility =
                    View.VISIBLE
                else findViewById<View>(R.id.jump_host).visibility = View.INVISIBLE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

    private val jumpHostListener: AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int, id: Long
            ) {
                val acct = parent
                    .getItemAtPosition(position) as Account
                jumpHostAccountId = acct.id
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                jumpHostAccountId = null
            }
        }

    fun confirm(ignoredView: View) {
        try {
            internalConfirm()
        } catch (e: Exception) {
            showError(this, "Error saving account", e)
        }
    }

    private fun internalConfirm() {
        val displayName = (findViewById<View?>(R.id.displayName) as EditText)
            .text.toString()

        val hostName = (findViewById<View?>(R.id.host) as EditText)
            .text.toString()

        val portString = (findViewById<View?>(R.id.port) as EditText)
            .text.toString()

        val userName = (findViewById<View?>(R.id.user) as EditText)
            .text.toString()

        val password = (findViewById<View?>(R.id.password) as EditText)
            .text.toString()
        val directory = (findViewById<View?>(R.id.start_directory) as EditText)
            .text.toString()
        val socksProxy = (findViewById<View?>(R.id.socks_proxy) as EditText)
            .text.toString()
        val hideFromList = (findViewById<View?>(R.id.hide_from_list) as CheckBox)
            .isChecked
        if (hostName.isEmpty() || portString.isEmpty() || userName.isEmpty()) return
        val port = portString.toInt()
        if (account != null) {
            // this is a request to edit an existing account

            // if nothing changed, exit

            if (displayName == account!!.displayName &&
                hostName == account!!.hostName &&
                userName == account!!.userName && port == account!!.port &&
                password.isEmpty() &&
                directory == account!!.directory &&
                (if (proxyType == PROXY_TYPE_SOCKS) socksProxy else "") == account!!.socksProxy && (if (proxyType == PROXY_TYPE_JUMP_HOST) jumpHostAccountId else null) == account!!.jumpHostId && hideFromList == account!!.hideFromList
            ) {
                Toast.makeText(
                    this,
                    R.string.nothing_changed,
                    Toast.LENGTH_SHORT
                )
                    .show()
                return
            }
        }

        val name = "$userName@$hostName:$port"
        if (account == null) {
            dao!!.insertAll(
                Account(
                    name, displayName,
                    hostName, port,
                    userName, password,
                    directory,
                    if (proxyType == PROXY_TYPE_SOCKS) socksProxy else "",
                    if (proxyType == PROXY_TYPE_JUMP_HOST) jumpHostAccountId else null,
                    hideFromList
                )
            )
            var flags = 0
            if (Build.VERSION.SDK_INT >= 30) flags = flags or ContentResolver.NOTIFY_INSERT
            notifyChange(flags)
        } else {
            // update existing account
            account!!.name = name
            account!!.displayName = displayName
            account!!.hostName = hostName
            account!!.port = port
            account!!.userName = userName
            if (!password.isEmpty()) account!!.password = password
            account!!.directory = directory
            account!!.socksProxy = if (proxyType == PROXY_TYPE_SOCKS) socksProxy else ""
            if (proxyType == PROXY_TYPE_JUMP_HOST) {
                require(
                    !dao!!.isDescendant(
                        jumpHostAccountId!!,
                        account!!.id
                    )
                ) { "Would cause loop in jump hosts" }
                require(jumpHostAccountId != account!!.id) { "Account cannot be jump host for itself" }
            }

            account!!.jumpHostId =
                if (proxyType == PROXY_TYPE_JUMP_HOST) jumpHostAccountId else null
            account!!.hideFromList = hideFromList
            dao!!.update(account!!)
            var flags = 0
            if (Build.VERSION.SDK_INT >= 30) flags = flags or ContentResolver.NOTIFY_UPDATE
            notifyChange(flags)
        }

        val result = Intent()
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        // id field name for intents
        const val ID_COL: String = "id"

        private const val PROXY_TYPE_NONE = 0
        private const val PROXY_TYPE_SOCKS = 1
        private const val PROXY_TYPE_JUMP_HOST = 2
    }
}
