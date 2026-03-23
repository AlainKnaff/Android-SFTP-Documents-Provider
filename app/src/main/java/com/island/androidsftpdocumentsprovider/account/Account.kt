package com.island.androidsftpdocumentsprovider.account;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
   Copyright (C) 2025,2026      Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(tableName = "roots",
	foreignKeys = [ForeignKey(entity = Account::class,
				  parentColumns = ["id"],
				  childColumns = ["jump_host"])],
	indices = [ Index(value= [ "jump_host" ]) ]
)
data class Account(@ColumnInfo(name="name") var name: String?,
	           @ColumnInfo(name="host_name") var hostName: String,
	           @ColumnInfo(name="port")var port: Int,
	           @ColumnInfo(name="user_name") var userName: String?,
	           @ColumnInfo(name="password") var password: String?,
	           @ColumnInfo(name="directory",
		               defaultValue="") var directory: String,
                   @ColumnInfo(name="socks_proxy",
                               defaultValue="") var socksProxy: String,
                   @ColumnInfo(name="jump_host")var jumpHostId: Int?,
) {
    @PrimaryKey(autoGenerate = true) var id: Int? = null
    override fun toString() : String {
	return name ?: "null"
    }
}
