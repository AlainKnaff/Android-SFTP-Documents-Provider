package com.island.androidsftpdocumentsprovider.account

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
   Copyright (C) 2025,2026      Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

@DatabaseView("""SELECT id,name,
                  NOT EXISTS(SELECT 1 FROM roots i
                             WHERE i.jump_host=o.id) can_remove
	          FROM roots o""")
data class AccountWithRemove(
    val id: Int,
    val name: String,
    @ColumnInfo(name="can_remove") val canRemove: Boolean
)
