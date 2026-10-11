package com.island.androidsftpdocumentsprovider.account

interface AccountIf {
    val name: String?
    val displayName: String?

    fun getUsedDisplayName() : String {
        val nm = displayName
        return if(!nm.isNullOrEmpty())
            nm
        else
            name?.replace(Regex(":22$"),"").orEmpty()
    }
}

