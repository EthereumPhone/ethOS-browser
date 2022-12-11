package de.baumann.browser.browser

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.http.HttpService
import java.lang.Long.parseLong
import java.math.BigDecimal
import java.math.BigInteger

class AndroidEthereum(
    context: Context
) {
    private val chainRPC = "https://cloudflare-eth.com"
    private val walletSDK = WalletSDK(
        context = context,
        web3RPC = chainRPC
    )

    val web3j = Web3j.build(HttpService(chainRPC))
    @JavascriptInterface
    fun getAddress(): String {
        return walletSDK.getAddress()
    }

    @JavascriptInterface
    fun signTransaction(
        transaction: String
    ): String {
        val txData = JSONObject(transaction)
        return walletSDK.sendTransaction(
            to = txData.getString("to"),
            value =  hexToBigInteger(txData.getString("value")),
            data = if (txData.has("data")) txData.getString("data") else "",
            gasAmount = hexToBigInteger(txData.getString("gas")),
            gasPriceVAL = if (txData.has("gasPrice")) hexToBigInteger(txData.getString("gasPrice")) else null,
        ).get()
    }

    @JavascriptInterface
    fun signMessage(
        message: String
    ): String {
        return walletSDK.signMessage(message).get()
    }

    @JavascriptInterface
    fun signTypedData(
        typedData: String
    ): String {
        return walletSDK.signMessage(typedData).get()
    }

    @JavascriptInterface
    fun getBlocknumber(): String {
        return web3j.ethBlockNumber().send().blockNumber.toString()
    }

    @JavascriptInterface
    fun ethCall(
        jsonStr: String
    ): String {
        val json = JSONObject(jsonStr)
        val to = json.getString("to")
        val data = json.getString("data")
        val returnEthCall = web3j.ethCall(
            org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                walletSDK.getAddress(),
                to,
                data
            ), DefaultBlockParameter.valueOf("latest")
        ).send()
        return returnEthCall.result
    }

    @JavascriptInterface
    fun estimateGas(
        jsonStr: String
    ): String {
        val json = JSONObject(jsonStr)
        val to = json.getString("to")
        val data = json.getString("data")
        val value = json.getString("value")
        // Estimate gas

        val gas = web3j.ethEstimateGas(
            org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                walletSDK.getAddress(),
                null,
                null,
                null,
                to,
                BigInteger(parseLong(value.substring(2), 16).toString()),
                data
            )
        ).send()
        println(gas.amountUsed)
        return gas.amountUsed.toString()
    }

    /**
     * Function to convert hex string into BigInteger string
     */
    private fun hexToBigInteger(hex: String): String {
        return BigInteger(hex.substring(2), 16).toString()
    }
}
