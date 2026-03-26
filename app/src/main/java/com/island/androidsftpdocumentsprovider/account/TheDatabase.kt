package com.island.androidsftpdocumentsprovider.account

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
   Copyright (C) 2026      Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.AutoMigration
import android.content.Context

@Database(entities = [Account::class],
	  views = [AccountWithRemove::class],
	  version = 4,
	  exportSchema = true,
	  autoMigrations = [
	      AutoMigration (from = 2, to = 3),
	      AutoMigration (from = 3, to = 4)
	  ]
)
abstract class TheDatabase : RoomDatabase() {
    abstract fun dao(): Dao

    // https://stackoverflow.com/questions/72048899/how-can-you-use-android-room-in-an-app-with-several-activities
    companion object {
        private var instance: TheDatabase? = null


        fun getInstance(context: Context): TheDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(context,
						TheDatabase::class.java,
						"roots")
                    .allowMainThreadQueries()
                    .build()
            }
            return instance as TheDatabase
        }

	@JvmStatic
	fun getDao(context: Context): Dao {
	    return getInstance(context).dao()
	}
    }
}
