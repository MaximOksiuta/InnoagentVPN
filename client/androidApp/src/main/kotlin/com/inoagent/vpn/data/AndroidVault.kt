package com.inoagent.vpn.data

import android.content.Context
import com.inoagent.vpn.shared.ClientVault

class AndroidVault(context:Context):ClientVault {
    private val preferences=context.getSharedPreferences("service-session",Context.MODE_PRIVATE)
    private val cipher=ConfigCipher()
    override fun read(key:String)=preferences.getString(key,null)?.let(cipher::decrypt)
    override fun write(key:String,value:String) { check(preferences.edit().putString(key,cipher.encrypt(value)).commit()) }
    override fun remove(key:String) { preferences.edit().remove(key).apply() }
}
