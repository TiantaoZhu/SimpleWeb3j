package com.tt.esayweb3j.impl

import com.tt.esayweb3j.EasyWalletErrCode
import com.tt.esayweb3j.EasyWalletException
import com.tt.esayweb3j.EasyWeb3JGlobalConfig
import com.tt.esayweb3j.MnemonicInvalidException
import org.web3j.crypto.EasyBip44WalletUtils
import org.web3j.crypto.MnemonicUtils
import java.io.File


object EasyWalletCenter {

    // 这里有所有解锁的钱包 具体用哪一个可以在外面找个变量存一下
    internal val unlockedWallets = mutableMapOf<String, EasyWalletProfile>()
    internal val nameToWalletMap = mutableMapOf<String, EasyWalletProfile>()

    fun getUnlockedWallet(walletName: String) = unlockedWallets[walletName]

    fun init() {
        EasyWeb3JGlobalConfig.walletBaseDir.mkdirs()
        loadAllWallet()
    }

    fun listAllWalletProfile(): List<EasyWalletProfile> {
        return nameToWalletMap.values.toMutableList().apply {
            sortByDescending { it.createTime }
        }
    }

    fun loadAllWallet() {
        kotlin.runCatching {
            EasyWeb3JGlobalConfig.walletBaseDir.takeIf { it.isDirectory }?.listFiles()?.filter {
                it.name.startsWith(
                    "0x"
                )
            }?.forEach { file ->
                kotlin.runCatching {
                    val readText = file.readText()
                    val ap = gson.fromJson(readText, EasyWalletProfile::class.java)
                    nameToWalletMap[ap.name] = ap
                }
            } ?: EasyWeb3JGlobalConfig.walletBaseDir.delete()

        }
    }

    fun deleteWallet(name: String) {
        val walletProfile =
            nameToWalletMap[name] ?: throw EasyWalletException(EasyWalletErrCode.WALLET_NOT_EXIST)
        File(EasyWeb3JGlobalConfig.walletBaseDir, walletProfile.walletFileName).delete()
        File(EasyWeb3JGlobalConfig.walletBaseDir, walletProfile.defaultEthAddress()).delete()
        nameToWalletMap.remove(name)
        unlockedWallets.remove(name)
    }

    fun generate(name: String, password: String): EasyWalletProfile {
        if (nameToWalletMap.containsKey(name)) {
            throw EasyWalletException(EasyWalletErrCode.WALLET_NAME_DUPLICATED)
        }
        val generateBip44Wallet =
            EasyBip44WalletUtils.generateBip44Wallet(password, EasyWeb3JGlobalConfig.walletBaseDir)

        return EasyWalletProfile.create(name, generateBip44Wallet).also {
            saveEasyWalletProfile(it)
            unlockedWallets[name] = it
        }
    }

    fun unlock(name: String, password: String): EasyWalletProfile {
        val walletProfile =
            nameToWalletMap[name] ?: throw EasyWalletException(EasyWalletErrCode.WALLET_NOT_EXIST)

        val walletFile = File(EasyWeb3JGlobalConfig.walletBaseDir, walletProfile.walletFileName)
        val easyBip44Wallet = kotlin.runCatching {
            EasyBip44WalletUtils.loadEasyBip44Wallet(password, walletFile)
        }.getOrElse {
            throw EasyWalletException(
                EasyWalletErrCode.PASSWORD_ERROR,
                "loadEasyBip44Wallet error",
                it
            )
        }

        val newProfile = walletProfile.copy(easyBip44Wallet = easyBip44Wallet)
        unlockedWallets[name] = newProfile
        return newProfile
    }

    fun lock(name: String) {
        unlockedWallets.remove(name)
    }

    fun changeName(oldName: String, newName: String) {
        val walletProfile =
            nameToWalletMap[oldName]
                ?: throw EasyWalletException(EasyWalletErrCode.WALLET_NOT_EXIST)
        nameToWalletMap.remove(oldName)
        val newProfile = walletProfile.copy(name = newName)
        saveEasyWalletProfile(newProfile)
    }

    fun recover(mnemonic: String, name: String, password: String): EasyWalletProfile {
        if (!MnemonicUtils.validateMnemonic(mnemonic)) {
            throw MnemonicInvalidException()
        }

        if (nameToWalletMap.containsKey(name)) {
            throw EasyWalletException(EasyWalletErrCode.WALLET_NAME_DUPLICATED)
        }

        findExistSameMnemonic(mnemonic)?.let {
            deleteWallet(it.name)
        }

        val generateBip44Wallet =
            EasyBip44WalletUtils.recoverBip44Wallet(
                mnemonic,
                password,
                EasyWeb3JGlobalConfig.walletBaseDir
            )

        return EasyWalletProfile.create(name, generateBip44Wallet).also {
            saveEasyWalletProfile(it)
            unlockedWallets[name] = it
        }
    }

    fun findExistSameMnemonic(mnemonic: String) = nameToWalletMap.values.find {
        it.defaultEthAddress() == EasyBip44WalletUtils.mnemonicToDefaultEthAddr(mnemonic)
    }

    fun hasExistSameMnemonic(mnemonic: String) = findExistSameMnemonic(mnemonic)?.name

    private fun saveEasyWalletProfile(profile: EasyWalletProfile) {
        val expectWalletFile =
            File(EasyWeb3JGlobalConfig.walletBaseDir, profile.defaultEthAddress())
        expectWalletFile.parentFile?.mkdirs()
        expectWalletFile.writeText(gson.toJson(profile))
        nameToWalletMap[profile.name] = profile
    }


}