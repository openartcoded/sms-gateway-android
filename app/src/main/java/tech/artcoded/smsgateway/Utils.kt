package tech.artcoded.smsgateway

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import java.security.KeyStore

private const val SHARED_PREFS_FILENAME = "sms_gateway_secret_shared_prefs"

object Utils {

    fun createEncryptedSharedPrefDestructively(context: Context): SharedPreferences {
        return try {
            createEncryptedSharedPref(context)
        } catch (e: GeneralSecurityException) {
            deleteMasterKeyEntry()
            deleteExistingPref(context)
            createEncryptedSharedPref(context)
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun deleteExistingPref( context: Context) {
        context.deleteSharedPreferences(SHARED_PREFS_FILENAME)
    }

    private fun deleteMasterKeyEntry() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    private fun createEncryptedSharedPref(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            SHARED_PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}