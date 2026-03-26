package com.island.androidsftpdocumentsprovider.account

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
   Copyright (C) 2026      Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Update

@Dao
interface Dao {
    @Query("SELECT * FROM roots")
    fun getAllAccounts(): MutableList<Account>

    @Query("SELECT * FROM roots WHERE NOT hide_from_list")
    fun getAllVisibleAccounts(): MutableList<Account>

    @Query("SELECT * FROM roots WHERE id = :id")
    fun readAccountById(id: Int): Account

    @Query("SELECT * FROM roots WHERE name = :name")
    fun readAccountByName(name: String): Account?

    @Query("""WITH RECURSIVE jhp AS
             (  SELECT :accountId child
               UNION
                SELECT a.id child
                FROM roots a JOIN jhp r ON a.jump_host=r.child )
             SELECT * FROM roots r
                      WHERE NOT EXISTS(SELECT 1 FROM jhp WHERE child = r.id)""")
    fun getAllEligibleJumpHosts(accountId: Int) : MutableList<Account>

    @Query("WITH RECURSIVE jhp AS ( SELECT jump_host jh FROM roots WHERE id = :child UNION select a.jump_host jh FROM roots a JOIN jhp r on a.id=r.jh ) SELECT COUNT(*) > 0 FROM jhp WHERE jh = :ancestor")
    fun isDescendant(child: Int, ancestor: Int) : Boolean

    @Insert
    fun insertAll(vararg targets: Account)

    @Update
    fun update(target: Account)

    @Delete
    fun delete(user: Account)
}
