package de.baumann.browser.browser

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.NotificationCompat
import de.baumann.browser.R
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import java.lang.Long.parseLong
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random
import java.util.concurrent.CompletableFuture


class AndroidEthereum(
    private val context: Context,
    private val webView: WebView
) {

    private var isEnabled = MyHashMapManager(context)
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
        chainId = walletSDK.getChainId().toString()
        initialSetBasedOnChainId()
        CompletableFuture.runAsync {
            Thread.sleep(10000)
            while (isRunning) {
                val newestChainIdNotHex = walletSDK.getChainId()

                (context as Activity).runOnUiThread {
                    if (newestChainIdNotHex != Integer.parseInt(chainId)) {
                        chainId = newestChainIdNotHex.toString()
                        initialSetBasedOnChainId()
                        webView.evaluateJavascript("window.ethereum.chainId=\"${toHexString(newestChainIdNotHex)}\"", null);
                        webView.evaluateJavascript("if (window.ethereum.savedChainChangedCallback != null) {\n" +
                                "    console.log('CHANGING CHAIN'); window.ethereum.savedChainChangedCallback(\"${toHexString(newestChainIdNotHex)}\")\n" +
                                "}", null)
                    }
                }
                Thread.sleep(1000)
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
                web3j = Web3j.build(HttpService("https://opt-mainnet.g.alchemy.com/v2/4CrNAPvukjJfB5UJYGLgqT_Q2HSkrnTP"))
                walletSDK = WalletSDK(
                    context = context,
                    web3RPC = "https://opt-mainnet.g.alchemy.com/v2/4CrNAPvukjJfB5UJYGLgqT_Q2HSkrnTP"
                )
            }
            42161 -> {
                web3j = Web3j.build(HttpService("https://arb-mainnet.g.alchemy.com/v2/nUK-9SVXiX2HYmdU-Pto7iPcPEm--euD"))
                walletSDK = WalletSDK(
                    context = context,
                    web3RPC = "https://arb-mainnet.g.alchemy.com/v2/nUK-9SVXiX2HYmdU-Pto7iPcPEm--euD"
                )
            }
            5 -> {
                web3j = Web3j.build(HttpService("https://eth-goerli.g.alchemy.com/v2/wEno3MttLG5usiVg4xL5_dXrDy_QH95f"))
                walletSDK = WalletSDK(
                    context = context,
                    web3RPC = "https://eth-goerli.g.alchemy.com/v2/wEno3MttLG5usiVg4xL5_dXrDy_QH95f"
                )
            }
        }
    }

    @JavascriptInterface
    fun getNewestChainId(): String {
        return toHexString(walletSDK.getChainId())
    }

    @JavascriptInterface
    fun getGasPrice(): String {
        val completableFuture = CompletableFuture<String>()
        (context as Activity).runOnUiThread {
            if (isEnabled.getHashMap()[getDomainName(webView.url!!)] == true) {
                completableFuture.complete(web3j.ethGasPrice().sendAsync().get().result)
            } else {
                completableFuture.complete("")
            }
        }
        return completableFuture.get()
    }

    @JavascriptInterface
    fun getAddress(): String {
        val completableFuture = CompletableFuture<String>()
        (context as Activity).runOnUiThread {
            if (isEnabled.getHashMap()[getDomainName(webView.url!!)] == true) {
                completableFuture.complete(walletSDK.getAddress())
            } else {
                completableFuture.complete("")
            }
        }
        return completableFuture.get()
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
        if (result == null) {
            Thread.sleep(500)
            return tryToGetTx(txHash)
        }
        return result
    }

    @JavascriptInterface
    fun addChain(chainObj: String): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "0x1"
        }

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
            text.setText("Do you want to switch chain to ${JSONObject(chainObj).getString("chainName")}?")
        }

        acceptButton.setOnClickListener {
            (context as Activity).runOnUiThread {
                view.removeView(mainView)
            }
            val chain = JSONObject(chainObj)
            val chainId = chain.getString("chainId")
            val rpcUrls = chain.getJSONArray("rpcUrls")
            walletSDK = WalletSDK(
                context = context,
                web3RPC = rpcUrls.getString(0)
            )
            web3j = Web3j.build(HttpService(rpcUrls.getString(0)))
            val newChainId = Integer.parseInt(hexToBigInteger(chainId))
            val result = walletSDK.changeChainId(newChainId).get()
            if (result == "done") {
                this.chainId = hexToBigInteger(chainId)
                completableFuture.complete(chainId)
            } else {
                completableFuture.complete("0x1")
            }
        }

        declineButton.setOnClickListener {
            (context as Activity).runOnUiThread {
                view.removeView(mainView)
            }
            completableFuture.complete("0x1")
        }
        (context as Activity).runOnUiThread {
            view.addView(mainView, params)
        }

        return completableFuture.get()
    }
    @JavascriptInterface
    fun getTransactionByHash(txHash: String): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == true)
        }
        if (compFut.get()) {
            val completableFuture = CompletableFuture<String>()
            CompletableFuture.runAsync {
                Thread.sleep(1000)
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
                        jsonObject.put("value", web3j.ethGetTransactionByHash(txHash).sendAsync().get().result.value.toString())
                    } catch (e: Exception) {
                        println("getTransactionByHash: error building json")
                        e.printStackTrace()
                    }

                    println("getTransactionByHash: json built")
                    completableFuture.complete(jsonObject.toString())
                } else {
                    completableFuture.complete("{}")
                }
            }
            return completableFuture.get()
        } else {
            return "{}"
        }
    }

    @JavascriptInterface
    fun getTransactionReceipt(txHash: String): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == true)
        }
        if (compFut.get()) {
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
                    completableFuture.complete("{}")
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
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == true)
        }
        if (compFut.get()) {
            val txData = JSONObject(transaction)
            val txHash = walletSDK.sendTransaction(
                to = txData.getString("to"),
                value =  hexToBigInteger(if (txData.has("value")) txData.getString("value") else "0x0"),
                data = if (txData.has("data")) txData.getString("data") else "",
                gasAmount = hexToBigInteger(txData.getString("gas")),
                gasPrice = if (txData.has("gasPrice")) hexToBigInteger(txData.getString("gasPrice")) else null,
                chainId = Integer.parseInt(chainId)
            ).get()
            return if (txHash == null) {
                "Transaction error: insufficient funds for gas * price + value"
            } else {
                if (txHash != WalletSDK.DECLINE) {
                    showNotificationForTx(txHash)
                    walletSDK.waitForTxToBeValidated(txHash).whenComplete { _, _ ->
                        showValidatedNotificationForTx(txHash)
                    }
                }
                txHash
            }
        } else {
            return "0"
        }
    }

    private fun showNotificationForTx(txHash: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = Random().nextInt(100000)
        val channelId = "channel-01"
        val channelName = "Transactions Notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mChannel = NotificationChannel(
                channelId, channelName, importance
            )
            notificationManager.createNotificationChannel(mChannel)
        }

        val mBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.browser_icon)
            .setContentTitle("Transaction published")
            .setContentText("Transaction published: $txHash")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(notificationId, mBuilder.build())
    }

    private fun showValidatedNotificationForTx(txHash: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = Random().nextInt(100000)
        val channelId = "channel-01"
        val channelName = "Transactions Notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mChannel = NotificationChannel(
                channelId, channelName, importance
            )
            notificationManager.createNotificationChannel(mChannel)
        }

        val mBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.browser_icon)
            .setContentTitle("Transaction included in block")
            .setContentText("Transaction included in block: $txHash")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(notificationId, mBuilder.build())
    }

    @JavascriptInterface
    fun signMessage(
        message: String,
        method: String
    ): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
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
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "0"
        }
        return walletSDK.signMessage(typedData, "eth_signTypedData").get()
    }

    @JavascriptInterface
    fun getBlocknumber(): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "0x0"
        }
        return "0x"+web3j.ethBlockNumber().send().blockNumber.toString(16)
    }

    @JavascriptInterface
    fun ethCall(
        jsonStr: String
    ): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "0"
        }
        val json = JSONObject(jsonStr)
        val from = if(json.has("from")) json.getString("from") else walletSDK.getAddress()
        val to = json.getString("to")
        val data = json.getString("data")
        val returnEthCall = web3j.ethCall(
            org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                from,
                to,
                data
            ), DefaultBlockParameter.valueOf("latest")
        ).send()

        if (returnEthCall.hasError()) {
            throw Exception("Transaction execution failed: ${returnEthCall.error.message}")
        }

        return returnEthCall.result ?: throw Exception("Transaction execution failed: execution reverted")
    }

    @JavascriptInterface
    fun estimateGas(
        jsonStr: String
    ): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "null"
        }

        val json = JSONObject(jsonStr)
        val to = json.getString("to")
        val data = json.getString("data")
        val value = if (json.has("value")) json.getString("value") else "0x0"
        val from = if (json.has("from")) json.getString("from") else walletSDK.getAddress()
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
            gas.error.data?.let {
                println("Error data: ${gas.error.data}")
            }
            return gas.error.message
        }
        println(gas.amountUsed)
        return gas.amountUsed.toString()
    }

    @JavascriptInterface
    fun getBlockByNumber(blockParamter: String, detailFlag: Boolean): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "0"
        }
        val blockStr = postEthGetBlockByNumber(blockParamter, detailFlag)
        val block = JSONObject(blockStr)
        return block.getJSONObject("result").toString()
    }

    fun postEthGetBlockByNumber(blockParamter: String, detailFlag: Boolean): String {
        val url = URL(chainRPC)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        val data = """
    {
        "jsonrpc": "2.0",
        "method": "eth_getBlockByNumber",
        "params": ["$blockParamter", $detailFlag],
        "id": 1
    }
    """.trimIndent()

        connection.outputStream.write(data.toByteArray())
        connection.outputStream.flush()

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            return response
        } else {
            throw Exception("Failed to post eth_getBlockByNumber request. Response code: $responseCode")
        }
    }

    /*
    Method to turn EthBlock.Block into a json string
     */
    fun ethBlockToJson(block: EthBlock.Block): String {
        val jsonObject = JSONObject()
        jsonObject.put("number", block.number)
        jsonObject.put("hash", block.hash)
        jsonObject.put("parentHash", block.parentHash)
        jsonObject.put("nonce", block.nonce)
        jsonObject.put("sha3Uncles", block.sha3Uncles)
        jsonObject.put("logsBloom", block.logsBloom)
        jsonObject.put("transactionsRoot", block.transactionsRoot)
        jsonObject.put("stateRoot", block.stateRoot)
        jsonObject.put("receiptsRoot", block.receiptsRoot)
        jsonObject.put("author", block.author)
        jsonObject.put("miner", block.miner)
        jsonObject.put("difficulty", block.difficulty)
        jsonObject.put("totalDifficulty", block.totalDifficulty)
        jsonObject.put("extraData", block.extraData)
        jsonObject.put("size", block.size)
        jsonObject.put("gasLimit", block.gasLimit)
        jsonObject.put("gasUsed", block.gasUsed)
        jsonObject.put("timestamp", block.timestamp)
        // Add transactions but as an array and fill that array with the correct json from the TransactionResult
        val transactions = JSONArray()
        for (transaction in block.transactions) {
            transactions.put(transaction.get().toString())
        }
        jsonObject.put("transactions", transactions)
        jsonObject.put("uncles", block.uncles)
        jsonObject.put("mixHash", block.mixHash)
        jsonObject.put("nonce", block.nonce)
        val outputString = jsonObject.toString()
        return outputString
    }

    @JavascriptInterface
    fun switchChain(chainId: String): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "0x1"
        }
        val newChainId = Integer.parseInt(hexToBigInteger(chainId))
        val result = walletSDK.changeChainId(newChainId).get()
        if (result == "done") {
            (context as Activity).runOnUiThread {
                webView.evaluateJavascript("window.ethereum.chainId=\"$chainId\"", null)
            }
            this.chainId = hexToBigInteger(chainId)
            initialSetBasedOnChainId()
            return chainId
        } else {
            return "0x1"
        }
    }

    @JavascriptInterface
    fun enableWallet(): String {
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == true)
        }
        if (compFut.get()) {
            return walletSDK.getAddress()
        }
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
            text.setText("Do you want to connect your ethOS wallet to ${getDomainName(webView.url!!)}?")
        }

        acceptButton.setOnClickListener {
            isEnabled.updateHashMap(getDomainName(webView.url!!), true)
            (context as Activity).runOnUiThread {
                view.removeView(mainView)
            }
            completableFuture.complete(walletSDK.getAddress())
        }

        declineButton.setOnClickListener {
            isEnabled.updateHashMap(getDomainName(webView.url!!), false)
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
