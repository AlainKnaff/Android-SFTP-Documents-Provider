package com.island.androidsftpdocumentsprovider.account

interface AccountIf {
    val name: String?
    val displayName: String?

    fun getUsedDisplayName() : String {
        val nm = displayName
        if(!nm.isNullOrEmpty())
            return nm
        else
	    return name?.replace(Regex(":22$"),"").orEmpty()
    }
}

