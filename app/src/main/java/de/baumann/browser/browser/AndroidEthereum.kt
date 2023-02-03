package de.baumann.browser.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import de.baumann.browser.R
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import java.lang.Long.parseLong
import java.math.BigInteger
import java.util.concurrent.CompletableFuture


class AndroidEthereum(
    private val context: Context,
    private val webView: WebView
) {
    private var enabled = false
    private val chainRPC = "https://eth-mainnet.g.alchemy.com/v2/AH4YE6gtBXoZf2Um-Z8Xr-6noHSZocKq"
    var chainId: String

    var walletSDK = WalletSDK(
        context = context,
        web3RPC = chainRPC
    )

    private var isRunning = true

    // FrameLayout from activity_main.xml
    var view = (context as Activity).findViewById<CoordinatorLayout>(R.id.main_layout)

    var web3j = Web3j.build(HttpService(chainRPC))

    init {
        chainId = walletSDK.getChainId().get().toString()
        initialSetBasedOnChainId()
        CompletableFuture.runAsync {
            Thread.sleep(10000)
            while (isRunning) {
                chainId = walletSDK.getChainId().toString()
                initialSetBasedOnChainId()
                (context as Activity).runOnUiThread {
                    webView.evaluateJavascript("window.ethereum.chainId=\"${toHexString(walletSDK.getChainId().get())}\"", null);
                }
            }
        }
    }

    private fun toHexString(i: Int): String {
        return "0x" + Integer.toHexString(i)
    }

    fun initialSetBasedOnChainId() {
        when(Integer.parseInt(chainId)) {
            1 -> {
                web3j = if (isLocalLightClientRunning()) {
                    Web3j.build(HttpService())
                } else {
                    Web3j.build(HttpService("https://eth-mainnet.g.alchemy.com/v2/AH4YE6gtBXoZf2Um-Z8Xr-6noHSZocKq"))
                }
                walletSDK = WalletSDK(
                    context = context,
                    web3RPC = "https://eth-mainnet.g.alchemy.com/v2/AH4YE6gtBXoZf2Um-Z8Xr-6noHSZocKq"
                )
            }
            10 -> {
                web3j = Web3j.build(HttpService("https://endpoints.omniatech.io/v1/op/mainnet/public"))
                walletSDK = WalletSDK(
                    context = context,
                    web3RPC = "https://endpoints.omniatech.io/v1/op/mainnet/public"
                )
            }
            42161 -> {
                web3j = Web3j.build(HttpService("https://arb1.arbitrum.io/rpc"))
                walletSDK = WalletSDK(
                    context = context,
                    web3RPC = "https://arb1.arbitrum.io/rpc"
                )
            }
            5 -> {
                web3j = Web3j.build(HttpService("https://rpc.ankr.com/eth_goerli"))
                walletSDK = WalletSDK(
                    context = context,
                    web3RPC = "https://rpc.ankr.com/eth_goerli"
                )
            }
        }
    }

    @JavascriptInterface
    fun getAddress(): String {
        if (enabled) {
            return walletSDK.getAddress()
        } else {
            return "0"
        }
    }
    fun tryToGetTxReceipt(txHash: String): TransactionReceipt? {
        val ethGetTransactionReceipt = web3j.ethGetTransactionReceipt(txHash).send()
        val result = ethGetTransactionReceipt.result
        println("ethGetTransactionReceipt: got result")
        return result
    }
    fun tryToGetTx(txHash: String): Transaction? {
        val ethGetTransactionByHash = web3j.ethGetTransactionByHash(txHash).send()
        val result = ethGetTransactionByHash.result
        println("ethGetTransactionByHash, got result")
        return result
    }
    @JavascriptInterface
    fun getTransactionByHash(txHash: String): String {
        if (enabled) {
            val completableFuture = CompletableFuture<String>()
            CompletableFuture.runAsync {
                println("getTransactionByHash: waiting for tx receipt")
                val txObj = tryToGetTx(txHash)
                println("getTransactionByHash: got tx")
                if (txObj != null) {
                    println("getTransactionByHash: tx is not null; building json")
                    val jsonObject = JSONObject()
                    try {
                        jsonObject.put("r", txObj.r)
                        jsonObject.put("s", txObj.s)
                        jsonObject.put("blockHash", if (txObj.blockHash == null) null else txObj.blockHash)
                        jsonObject.put("from", txObj.from)
                        jsonObject.put("hash", txObj.hash)
                        jsonObject.put("input", txObj.input)
                        jsonObject.put("to", txObj.to)
                        jsonObject.put("v", txObj.v)
                        jsonObject.put("blockNumber", if (txObj.blockNumberRaw == null) null else txObj.blockNumber)
                        jsonObject.put("gas", txObj.gas)
                        jsonObject.put("gasPrice", txObj.gasPrice)
                        jsonObject.put("nonce", txObj.nonce)
                        jsonObject.put("transactionIndex", if (txObj.transactionIndexRaw == null) null else txObj.transactionIndex)
                        jsonObject.put("value", web3j.ethGetTransactionByHash(txHash).sendAsync().get().result.value)
                    } catch (e: Exception) {
                        println("getTransactionByHash: error building json")
                        e.printStackTrace()
                    }

                    println("getTransactionByHash: json built")
                    completableFuture.complete(jsonObject.toString())
                } else {
                    completableFuture.complete("0")
                }
            }
            return completableFuture.get()
        } else {
            return "{}"
        }
    }

    @JavascriptInterface
    fun getTransactionReceipt(txHash: String): String {
        if (enabled) {
            val completableFuture = CompletableFuture<String>()
            CompletableFuture.runAsync {
                println("getTransactionReceipt: waiting for tx receipt")
                val txObj = tryToGetTxReceipt(txHash)
                if (txObj != null) {
                    val jsonObject = JSONObject()
                    jsonObject.put("blockHash", txObj.blockHash)
                    jsonObject.put("contractAddress", if (txObj.contractAddress == null) null else txObj.contractAddress)
                    jsonObject.put("from", txObj.from)
                    jsonObject.put("logsBloom", txObj.logsBloom)
                    jsonObject.put("to", if(txObj.to == null) null else txObj.to)
                    jsonObject.put("transactionHash", txObj.transactionHash)
                    jsonObject.put("blockNumber", txObj.blockNumber)
                    jsonObject.put("cumulativeGasUsed", txObj.cumulativeGasUsed)
                    jsonObject.put("gasUsed", txObj.gasUsed)
                    jsonObject.put("status", txObj.status)
                    jsonObject.put("transactionIndex", txObj.transactionIndex)
                    val logsJsonArray = JSONArray()
                    for (log in txObj.logs) {
                        val logJsonObject = JSONObject()
                        logJsonObject.put("address", log.address)
                        logJsonObject.put("blockHash", log.blockHash)
                        logJsonObject.put("blockNumber", if (log.blockNumberRaw == null) null else log.blockNumber)
                        logJsonObject.put("data", log.data)
                        logJsonObject.put("logIndex", if (log.logIndexRaw == null) null else log.logIndex)
                        logJsonObject.put("removed", log.isRemoved)
                        val topicsJsonArray = JSONArray()
                        for (topic in log.topics) {
                            topicsJsonArray.put(topic)
                        }
                        logJsonObject.put("topics", topicsJsonArray)
                        logJsonObject.put("transactionHash", log.transactionHash)
                        logJsonObject.put("transactionIndex", if (log.transactionIndexRaw == null) null else log.transactionIndex)
                        logsJsonArray.put(logJsonObject)
                    }
                    jsonObject.put("logs", logsJsonArray)
                    completableFuture.complete(jsonObject.toString())
                } else {
                    completableFuture.complete("0")
                }
            }
            return completableFuture.get()
        } else {
            return "{}"
        }
    }

    @JavascriptInterface
    fun signTransaction(
        transaction: String
    ): String {
        if (enabled) {
            val txData = JSONObject(transaction)
            val txHash = walletSDK.sendTransaction(
                to = txData.getString("to"),
                value =  hexToBigInteger(if (txData.has("value")) txData.getString("value") else "0x0"),
                data = if (txData.has("data")) txData.getString("data") else "",
                gasAmount = hexToBigInteger(txData.getString("gas")),
                gasPrice = if (txData.has("gasPrice")) hexToBigInteger(txData.getString("gasPrice")) else null,
                chainId = Integer.parseInt(chainId)
            ).get()
            return txHash
        } else {
            return "0"
        }
    }

    @JavascriptInterface
    fun signMessage(
        message: String,
        method: String
    ): String {
        if (!enabled) {
            return "0"
        }
        return if (method == "personal_sign") {
            val response = walletSDK.signMessage(message, "personal_sign_hex").get()
            println(response)
            response
        } else if (method == "eth_sign") {
            val response =  walletSDK.signMessage(message, "personal_sign").get()
            println(response)
            response
        } else if (method == "eth_signTypedData") {
            val mess = message.replace("\\", "")
            val response = walletSDK.signMessage(mess.substring(1, mess.length - 1), "eth_signTypedData").get()
            println(response)
            response
        } else {
            "0"
        }
    }

    @JavascriptInterface
    fun signTypedData(
        typedData: String,
        method: String
    ): String {
        println("signTypedData: $typedData")
        if (!enabled) {
            return "0"
        }
        return walletSDK.signMessage(typedData, "eth_signTypedData").get()
    }

    @JavascriptInterface
    fun getBlocknumber(): String {
        if (!enabled) {
            return "0x0"
        }
        return "0x"+web3j.ethBlockNumber().send().blockNumber.toString(16)
    }

    @JavascriptInterface
    fun ethCall(
        jsonStr: String
    ): String {
        if (!enabled) {
            return "0"
        }
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
        if (!enabled) {
            return "0"
        }
        val json = JSONObject(jsonStr)
        val to = json.getString("to")
        val data = json.getString("data")
        val value = if (json.has("value")) json.getString("value") else "0x0"
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
        if (gas.hasError()) {
            println("Error: ${gas.error.message}")
            return "0x0"
        }
        println(gas.amountUsed)
        return gas.amountUsed.toString()
    }

    @JavascriptInterface
    fun switchChain(chainId: String): String {
        if (!enabled) {
            return "0x1"
        }
        val newChainId = Integer.parseInt(hexToBigInteger(chainId))
        val result = walletSDK.changeChainId(newChainId).get()
        if (result == "done") {
            (context as Activity).runOnUiThread {
                webView.evaluateJavascript("window.ethereum.chainId=\"$chainId\"", null)
            }
            return chainId
        } else {
            return "0x1"
        }
    }

    @JavascriptInterface
    fun enableWallet(): String {
        val completableFuture = CompletableFuture<String>()
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.RGBA_8888
        )
        params.gravity = Gravity.CENTER or Gravity.BOTTOM

        val mainView = inflater.inflate(R.layout.enablelayout, null)

        val acceptButton = mainView.findViewById<com.google.android.material.button.MaterialButton>(R.id.acceptbtn)
        val declineButton = mainView.findViewById<com.google.android.material.button.MaterialButton>(R.id.declinebtn)
        val text = mainView.findViewById<TextView>(R.id.choosemethod)
        (context as Activity).runOnUiThread {
            text.setText("Do you want to connect your wallet to ${getDomainName(webView.url!!)}?")
        }

        acceptButton.setOnClickListener {
            enabled = true
            (context as Activity).runOnUiThread {
                view.removeView(mainView)
            }
            completableFuture.complete(walletSDK.getAddress())
        }

        declineButton.setOnClickListener {
            enabled = false
            (context as Activity).runOnUiThread {
                view.removeView(mainView)
            }
            completableFuture.complete("declined")
        }
        (context as Activity).runOnUiThread {
            view.addView(mainView, params)
        }

        return completableFuture.get()
    }

    /**
     * Function to convert hex string into BigInteger string
     */
    private fun hexToBigInteger(hex: String): String {
        return BigInteger(hex.substring(2), 16).toString()
    }

    /**
     * Function to turn webview url into domain name
     */
    private fun getDomainName(url: String): String {
        val uri = Uri.parse(url)
        val domain = uri.host
        return domain.orEmpty()
    }

    fun isLocalLightClientRunning() : Boolean {
        val web3jl = Web3j.build(HttpService())
        return try {
            web3jl.ethBlockNumber().sendAsync().get()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Function to sign a message with "personal_sign" method, so that it can be verified with web3js in javascript

    private fun sign(message: String, credentials: Credentials): String {
        val messageBytes: ByteArray = hexToString(message.substring(2)).toByteArray(StandardCharsets.UTF_8)
        val signature = Sign.signPrefixedMessage(messageBytes, credentials.ecKeyPair)
        val r = Numeric.toHexString(signature.r)
        val s = Numeric.toHexString(signature.s).substring(2)
        val v = Numeric.toHexString(signature.v).substring(2)
        println("$r    $s    $v")
        return java.lang.StringBuilder(r)
            .append(s)
            .append(v)
            .toString()
    }

    fun hexToString(hex: String): String {
        val sb = StringBuilder()
        val temp = StringBuilder()

        // 49204c6f7665204a617661 split into two characters 49, 20, 4c...
        var i = 0
        while (i < hex.length - 1) {


            // grab the hex in pairs
            val output = hex.substring(i, i + 2)
            // convert hex to decimal
            val decimal = output.toInt(16)
            // convert the decimal to character
            sb.append(decimal.toChar())
            temp.append(decimal)
            i += 2
        }
        return sb.toString()
    }
     */

}
