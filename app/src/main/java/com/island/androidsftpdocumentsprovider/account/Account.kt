package com.island.androidsftpdocumentsprovider.account;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
   Copyright (C) 2025,2026      Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

class Account(var id: Int,
	      var name: String?,
	      var hostName: String,
	      var port: Int,
	      var userName: String?,
	      var password: String?,      
	      var directory: String) {
}
