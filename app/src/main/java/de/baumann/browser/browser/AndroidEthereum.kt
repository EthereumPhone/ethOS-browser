package de.baumann.browser.browser

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import de.baumann.browser.R

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ethereumphone.walletsdk.WalletSDK
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
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
import java.util.concurrent.atomic.AtomicBoolean


class AndroidEthereum(
    private val context: Context,
    private val webView: WebView,
) {

    private var isEnabled = MyHashMapManager(context)
    private val chainRPC = "https://eth-mainnet.g.alchemy.com/v2/AH4YE6gtBXoZf2Um-Z8Xr-6noHSZocKq"
    var chainId: String

    var walletSDK = WalletSDK(
        context = context,
        web3jInstance = Web3j.build(HttpService(chainRPC))
    )

    private var connections: List<String> = arrayListOf()

    private val atomicBoolean = AtomicBoolean(true)

    // FrameLayout from activity_main.xml
    var view = (context as Activity).findViewById<CoordinatorLayout>(R.id.main_layout)

    var web3j = Web3j.build(HttpService(chainRPC))

    val projectId = "fd790311be10b88652a7c5a326bcfedb" // Get Project ID at https://cloud.walletconnect.com/
    val relayUrl = "relay.walletconnect.com"
    val serverUrl = "wss://$relayUrl?projectId=$projectId"
    val connectionType = ConnectionType.AUTOMATIC
    val appMetaData = Core.Model.AppMetaData(
        name = "ethOS Wallet",
        description = "",
        url = "",
        icons = arrayListOf(""),
        redirect = "" // Custom Redirect URI
    )
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)



    init {
        println("CLOSED: NEW ANDROID ETHEREUM")
        chainId = "0x1"
        GlobalScope.launch(Dispatchers.IO) {
            chainId = walletSDK.getChainId().toString()
            val rpcUrl = getCorrectRPC(walletSDK.getChainId())
            walletSDK = WalletSDK(
                context = context,
                web3jInstance = Web3j.build(HttpService(rpcUrl))
            )
            updateWeb3jInstance(rpcUrl)
            GlobalScope.launch(Dispatchers.IO) {
                while (atomicBoolean.get()) {
                    val newestChainIdNotHex = walletSDK.getChainId()

                    (context as Activity).runOnUiThread {
                        if (newestChainIdNotHex != Integer.parseInt(chainId)) {
                            println("ethosdebug: CHAIN WAS CHANGED TO $newestChainIdNotHex")
                            chainId = newestChainIdNotHex.toString()
                            web3j = Web3j.build(HttpService(getCorrectRPC(newestChainIdNotHex)))
                            walletSDK = WalletSDK(
                                context = context,
                                web3jInstance = web3j
                            )
                            webView.evaluateJavascript("window.ethereum.chainId=\"${toHexString(newestChainIdNotHex)}\"", null);
                            webView.evaluateJavascript("if (window.ethereum.savedChainChangedCallback != null) {\n" +
                                    "    console.log('CHANGING CHAIN'); window.ethereum.savedChainChangedCallback(\"${toHexString(newestChainIdNotHex)}\")\n" +
                                    "}", null)

                            webView.post {
                                webView.evaluateJavascript("javascript:window.ethereum._triggerChainChanged('${bigIntegerToHex(BigInteger(newestChainIdNotHex.toString()))}')", null)
                            }

                            sharedPreferences.getString(getDomainName(webView.url!!)+"_topic", null)?.let {
                                Web3Wallet.emitSessionEvent(
                                    params = Wallet.Params.SessionEmit(
                                        topic = it,
                                        event = Wallet.Model.SessionEvent(
                                            name = "chainChanged",
                                            data = "eip155:$newestChainIdNotHex"
                                        ),
                                        chainId = newestChainIdNotHex.toString()
                                    ),
                                    onError = {
                                        println("Error: $it")
                                    }
                                )
                            }
                        }
                    }
                    Thread.sleep(1000)
                }
            }
        }





        CoreClient.initialize(relayServerUrl = serverUrl, connectionType = connectionType, application = context.applicationContext as Application, metaData = appMetaData, onError = {})

        val initParams = Wallet.Params.Init(core = CoreClient)

        Web3Wallet.initialize(initParams) { error ->
            // Error will be thrown if there's an issue during initialization
        }

        val walletDelegate = object : Web3Wallet.WalletDelegate {
            override fun onSessionProposal(sessionProposal: Wallet.Model.SessionProposal) {
                // Triggered when wallet receives the session proposal sent by a Dapp
                val proposerPublicKey: String = sessionProposal.proposerPublicKey
                val supportedNamespaces = mapOf(
                    "eip155" to Wallet.Model.Namespace.Session(
                        chains = listOf("eip155:1", "eip155:7777777"),
                        methods = listOf("personal_sign", "eth_sendTransaction", "eth_signTransaction", "wallet_addEthereumChain"),
                        events = listOf("chainChanged", "accountsChanged"),
                        accounts = listOf("eip155:1:"+walletSDK.getAddress(), "eip155:7777777:"+walletSDK.getAddress())
                    )
                )

                val approveParams: Wallet.Params.SessionApprove = Wallet.Params.SessionApprove(proposerPublicKey, supportedNamespaces)
                Web3Wallet.approveSession(approveParams) { error ->
                    println("Error: $error")
                }
                (context as Activity).runOnUiThread {
                    isEnabled.updateHashMap(getDomainName(webView.url!!), true)
                    sharedPreferences.edit().putString(getDomainName(webView.url!!)+"_topic", sessionProposal.pairingTopic).apply()
                }
            }

            override fun onSessionRequest(sessionRequest: Wallet.Model.SessionRequest) {
                // Triggered when a Dapp sends SessionRequest to sign a transaction or a message
                val sessionTopic: String = sessionRequest.topic
                (context as Activity).runOnUiThread {
                    sharedPreferences.edit().putString(getDomainName(webView.url!!)+"_topic", sessionTopic).apply()
                }
                val params = sessionRequest.request.params
                val method = sessionRequest.request.method
                when(method) {
                    "personal_sign" ->  {
                        GlobalScope.launch(Dispatchers.IO) {
                            val jsonRpcResponse: Wallet.Model.JsonRpcResponse.JsonRpcResult = Wallet.Model.JsonRpcResponse.JsonRpcResult(
                                id = sessionRequest.request.id,
                                result = signMessage(extractFirstValueWithoutQuotes(params)!!, method)
                            )
                            val result = Wallet.Params.SessionRequestResponse(sessionTopic = sessionTopic, jsonRpcResponse = jsonRpcResponse)

                            Web3Wallet.respondSessionRequest(result) { error ->
                                println("Error: $error")
                            }
                        }
                    }
                    "wallet_addEthereumChain" -> {
                        val jsonRpcResponse: Wallet.Model.JsonRpcResponse.JsonRpcResult = Wallet.Model.JsonRpcResponse.JsonRpcResult(
                            id = sessionRequest.request.id,
                            result = addChain(params.substring(1, params.length-1))
                        )
                        val result = Wallet.Params.SessionRequestResponse(sessionTopic = sessionTopic, jsonRpcResponse = jsonRpcResponse)

                        Web3Wallet.respondSessionRequest(result) { error ->
                            println("Error: $error")
                        }
                    }
                    "eth_sendTransaction" -> {
                        GlobalScope.launch(Dispatchers.IO) {
                            val jsonRpcResponse: Wallet.Model.JsonRpcResponse.JsonRpcResult = Wallet.Model.JsonRpcResponse.JsonRpcResult(
                                id = sessionRequest.request.id,
                                result = signTransaction(params.substring(1, params.length-1))
                            )
                            val result = Wallet.Params.SessionRequestResponse(sessionTopic = sessionTopic, jsonRpcResponse = jsonRpcResponse)

                            Web3Wallet.respondSessionRequest(result) { error ->
                                println("Error: $error")
                            }
                        }
                    }
                }


            }

            override fun onAuthRequest(authRequest: Wallet.Model.AuthRequest) {
                // Triggered when Dapp / Requester makes an authorization request
            }

            override fun onSessionDelete(sessionDelete: Wallet.Model.SessionDelete) {
                // Triggered when the session is deleted by the peer
            }

            override fun onSessionSettleResponse(settleSessionResponse: Wallet.Model.SettledSessionResponse) {
                // Triggered when wallet receives the session settlement response from Dapp
            }

            override fun onSessionUpdateResponse(sessionUpdateResponse: Wallet.Model.SessionUpdateResponse) {
                // Triggered when wallet receives the session update response from Dapp
            }

            override fun onConnectionStateChange(state: Wallet.Model.ConnectionState) {
                //Triggered whenever the connection state is changed

            }

            override fun onError(error: Wallet.Model.Error) {
                // Triggered whenever there is an issue inside the SDK
                println("Error: $error")
            }
        }
        Web3Wallet.setWalletDelegate(walletDelegate)


    }

    fun updateWeb3jInstance(rpcUrl: String) {
        web3j = Web3j.build(HttpService(rpcUrl))
    }

    fun extractFirstValueWithoutQuotes(arrayString: String): String? {
        // Remove the leading and trailing brackets and split the string by commas
        val elements = arrayString.removeSurrounding("[", "]").split(",\\s*".toRegex())

        // Return the first element of the array without quotes and whitespace
        return elements.firstOrNull()?.trim()?.removeSurrounding("\"")
    }

    fun connectToWalletConnect(uri: String) {
        Web3Wallet.pair(
            Wallet.Params.Pair(uri),
            onSuccess = {
                println("SUCCESS")
            },
            onError = {
                println("ON ERROR: $it")
            }
        )
    }

    private fun toHexString(i: Int): String {
        return "0x" + Integer.toHexString(i)
    }

    fun stop() {
        atomicBoolean.set(false)
    }


    fun getCorrectRPC(chainId: Int): String {
        return when(chainId) {
            1 -> "https://eth-mainnet.g.alchemy.com/v2/AH4YE6gtBXoZf2Um-Z8Xr-6noHSZocKq"
            10 -> "https://opt-mainnet.g.alchemy.com/v2/4CrNAPvukjJfB5UJYGLgqT_Q2HSkrnTP"
            42161 -> "https://arb-mainnet.g.alchemy.com/v2/nUK-9SVXiX2HYmdU-Pto7iPcPEm--euD"
            5 -> "https://eth-goerli.g.alchemy.com/v2/wEno3MttLG5usiVg4xL5_dXrDy_QH95f"
            7777777 -> "https://rpc.zora.energy"
            137 -> "https://polygon-mainnet.g.alchemy.com/v2/OcU1X_dJ0EPxn2DzICOkL3JcaNuvZzbT"
            8453 -> "https://base-mainnet.g.alchemy.com/v2/ARPkWAyUzQM4JoU8OsAeVgNP18_yfIA-"
            else -> "https://eth-mainnet.g.alchemy.com/v2/AH4YE6gtBXoZf2Um-Z8Xr-6noHSZocKq"
        }
    }

    @JavascriptInterface
    fun getNewestChainId(): String {
        val future = CompletableFuture<String>()
        GlobalScope.launch {
            future.complete(toHexString(walletSDK.getChainId()))
        }
        return future.get()
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
    fun getBalance(address: String): String {
        val completableFuture = CompletableFuture<String>()
        (context as Activity).runOnUiThread {
            if (isEnabled.getHashMap()[getDomainName(webView.url!!)] == true) {
                try {
                    // Fetching the balance for the specified address
                    val balance = web3j.ethGetBalance(
                        address, DefaultBlockParameterName.LATEST
                    ).sendAsync().get().balance

                    // Completing the future with the balance (in hexadecimal format)
                    completableFuture.complete(balance.toString(16))
                } catch (e: Exception) {
                    completableFuture.completeExceptionally(e)
                }
            } else {
                completableFuture.complete("")
            }
        }
        return completableFuture.get()
    }


    @JavascriptInterface
    fun getTransactionCount(address: String): String {
        val completableFuture = CompletableFuture<String>()
        (context as Activity).runOnUiThread {
            if (isEnabled.getHashMap()[getDomainName(webView.url!!)] == true) {
                try {
                    // Fetching the transaction count for the specified address
                    val transactionCount = web3j.ethGetTransactionCount(
                        address, DefaultBlockParameterName.LATEST
                    ).sendAsync().get().transactionCount

                    // Completing the future with the transaction count (in hexadecimal format)
                    completableFuture.complete(transactionCount.toString(16))
                } catch (e: Exception) {
                    completableFuture.completeExceptionally(e)
                }
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
                web3jInstance = Web3j.build(HttpService(rpcUrls.getString(0)))
            )
            web3j = Web3j.build(HttpService(rpcUrls.getString(0)))
            val newChainId = Integer.parseInt(hexToBigInteger(chainId))
            GlobalScope.launch {
                val result = walletSDK.changeChain(
                    chainId = newChainId,
                    rpcEndpoint = rpcUrls.getString(0)
                )
                updateWeb3jInstance(rpcUrls.getString(0))
                completableFuture.complete(result)
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
        val endVal = completableFuture.get()
        if (endVal == "done") {
            chainId = hexToBigInteger(chainId)
            completableFuture.complete(chainId)
        } else {
            completableFuture.complete("0x1")
        }
        return endVal
    }
    @JavascriptInterface
    fun getTransactionByHash(txHash: String): String {
        val compFut = CompletableFuture<Boolean>()

        // Run the domain check on the UI thread.
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == true)
        }

        try {
            // Proceed only if the domain is enabled.
            if (compFut.get()) {
                val transactionFuture = CompletableFuture<String>()

                CompletableFuture.runAsync {
                    try {
                        println("getTransactionByHash: waiting for tx receipt")
                        val txObj = tryToGetTx(txHash)
                        println("getTransactionByHash: got tx")

                        val jsonObject = JSONObject().apply {
                            if (txObj != null) {
                                println("getTransactionByHash: tx is not null; building json")
                                put("r", txObj.r)
                                put("s", txObj.s)
                                put("blockHash", txObj.blockHash)
                                put("from", txObj.from)
                                put("hash", txObj.hash)
                                put("input", txObj.input)
                                put("to", txObj.to)
                                put("v", txObj.v)
                                put("blockNumber", txObj.blockNumberRaw)
                                put("gas", txObj.gas)
                                put("gasPrice", txObj.gasPrice)
                                put("nonce", txObj.nonce)
                                put("transactionIndex", txObj.transactionIndexRaw)
                                put("value", txObj.value.toString()) // Assuming txObj.value is already a String type or has a meaningful toString() implementation.
                            } else {
                                put("error", "Transaction not found")
                            }
                        }

                        println("getTransactionByHash: json built")
                        transactionFuture.complete(jsonObject.toString())
                    } catch (e: Exception) {
                        println("getTransactionByHash: error building json")
                        e.printStackTrace()
                        transactionFuture.complete(JSONObject().put("error", "Exception occurred: ${e.message}").toString())
                    }
                }

                // Return the result of the future.
                return transactionFuture.get()
            }
        } catch (e: Exception) {
            // Handle exceptions from future operations.
            e.printStackTrace()
        }

        // If we reach this point, either the domain is not enabled or an exception occurred.
        return JSONObject().put("error", "Domain is not enabled or an error occurred").toString()
    }

    @JavascriptInterface
    fun getTransactionReceipt(txHash: String): String {
        val compFut = CompletableFuture<Boolean>()

        // Run check on UI thread to determine if the domain is enabled.
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == true)
        }

        try {
            // Only proceed if domain is enabled.
            if (compFut.get()) {
                val transactionReceipt = CompletableFuture<String>()

                // Fetch transaction receipt on a background thread.
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val txObj = tryToGetTxReceipt(txHash)
                        val jsonObject = JSONObject().apply {
                            if (txObj != null) {
                                put("blockHash", txObj.blockHash)
                                put("contractAddress", txObj.contractAddress)
                                put("from", txObj.from)
                                put("logsBloom", txObj.logsBloom)
                                put("to", txObj.to)
                                put("transactionHash", txObj.transactionHash)
                                put("blockNumber", txObj.blockNumber)
                                put("cumulativeGasUsed", txObj.cumulativeGasUsed)
                                put("gasUsed", txObj.gasUsed)
                                put("status", txObj.status)
                                put("transactionIndex", txObj.transactionIndex)
                                put("logs", JSONArray(txObj.logs.map { log ->
                                    JSONObject().apply {
                                        put("address", log.address)
                                        put("blockHash", log.blockHash)
                                        put("blockNumber", log.blockNumberRaw)
                                        put("data", log.data)
                                        put("logIndex", log.logIndexRaw)
                                        put("removed", log.isRemoved)
                                        put("topics", JSONArray(log.topics))
                                        put("transactionHash", log.transactionHash)
                                        put("transactionIndex", log.transactionIndexRaw)
                                    }
                                }))
                            } else {
                                put("error", "Transaction receipt not found")
                            }
                        }
                        transactionReceipt.complete(jsonObject.toString())
                    } catch (e: Exception) {
                        transactionReceipt.completeExceptionally(e)
                    }
                }
                return transactionReceipt.get()
            }
        } catch (e: Exception) {
            // Log the exception, and consider handling it appropriately.
            e.printStackTrace()
        }

        // Return an error JSON if the domain is not enabled or in case of any exception.
        return JSONObject().put("error", "Domain is not enabled or an error occurred").toString()
    }


    @JavascriptInterface
    fun signTransaction(
        transaction: String
    ): String {
        println("Launching the signTx method")
        val future = CompletableFuture<String>();
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == true)
        }
        if (compFut.get()) {
            GlobalScope.launch {
                val gasPrice = increaseByFivePercent(web3j.ethGasPrice().send().gasPrice)

                val txData = JSONObject(transaction)
                val txHash = walletSDK.sendTransaction(
                    to = txData.getString("to"),
                    value =  hexToBigInteger(if (txData.has("value")) txData.getString("value") else "0x0"),
                    data = if (txData.has("data")) txData.getString("data") else "",
                    gasAmount = if (txData.has("gas")) hexToBigInteger(txData.getString("gas")) else estimateGas(transaction),
                    gasPrice = if (txData.has("gasPrice")) hexToBigInteger(txData.getString("gasPrice")) else gasPrice.toString(),
                )
                if (txHash != WalletSDK.DECLINE) {
                    showNotificationForTx(txHash)
                    waitForTxToBeValidated(txHash).whenComplete { _, _ ->
                        showValidatedNotificationForTx(txHash)
                    }
                }
                future.complete(txHash)
            }
            return future.get()
        } else {
            return "0"
        }
    }

    fun increaseByFivePercent(value: BigInteger): BigInteger {
        // Create a BigInteger representation of 105
        val multiplier = BigInteger.valueOf(105)

        // Create a BigInteger representation of 100 for the divisor
        val divisor = BigInteger.valueOf(100)

        // Increase value by 5%
        // Equivalent to: value * 105 / 100
        return value.multiply(multiplier).divide(divisor)
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
            .setSmallIcon(R.drawable.notification_icon)
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
            .setSmallIcon(R.drawable.notification_icon)
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
        val future = CompletableFuture<String>();
        val compFut = CompletableFuture<Boolean>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "0"
        }
        GlobalScope.launch {
            val result = when (method) {
                "personal_sign" -> {
                    val response = walletSDK.signMessage(message, "personal_sign_hex")
                    println(response)
                    response
                }
                "eth_sign" -> {
                    val response =  walletSDK.signMessage(message, "personal_sign")
                    println(response)
                    response
                }
                "eth_signTypedData" -> {
                    val mess = message.replace("\\", "")
                    val response = walletSDK.signMessage(mess.substring(1, mess.length - 1), "eth_signTypedData")
                    println(response)
                    response
                }
                else -> {
                    "0"
                }
            }
            future.complete(result)
        }

        return future.get()
    }

    @JavascriptInterface
    fun signTypedData(
        typedData: String
    ): String {
        println("signTypedData: $typedData")
        val compFut = CompletableFuture<Boolean>()
        val future = CompletableFuture<String>()
        (context as Activity).runOnUiThread {
            compFut.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == false)
        }
        if (compFut.get()) {
            return "0"
        }
        val realTypedData = typedData.replace("\\", "")
        GlobalScope.launch {
            val resultSign = walletSDK.signMessage(realTypedData.substring(1, realTypedData.length - 1), "eth_signTypedData")
            future.complete(resultSign)
        }
        return future.get()
    }

    fun waitForTxToBeValidated(txHash: String): CompletableFuture<Unit> {
        val completableFuture = CompletableFuture<Unit>()
        CompletableFuture.runAsync {
            while (true) {
                val receipt = web3j!!.ethGetTransactionReceipt(txHash).sendAsync().get()
                if (receipt.hasError()) {
                    println("Error: ${receipt.error.message}")
                    completableFuture.completeExceptionally(Exception(receipt.error.message))
                    return@runAsync
                }
                if (receipt.result != null) {
                    println("Transaction validated!")
                    completableFuture.complete(Unit)
                    return@runAsync
                }
                Thread.sleep(1000)
            }
        }

        return completableFuture
    }


    @JavascriptInterface
    fun getBlockNumber(): String {
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

        return try {
            block.getJSONObject("result").toString()
        } catch (e: Exception) {
            "null"
        }
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
        val isEnabledFuture = CompletableFuture<Boolean>()
        val future = CompletableFuture<String>()
        (context as Activity).runOnUiThread {
            isEnabledFuture.complete(isEnabled.getHashMap()[getDomainName(webView.url!!)] == true)
        }

        if (!isEnabledFuture.get()) {
            throw Exception("Domain is not enabled")
        }

        val newChainId = Integer.parseInt(hexToBigInteger(chainId))
        GlobalScope.launch {
            val newRPC = getCorrectRPC(newChainId)
            updateWeb3jInstance(newRPC)
            future.complete(walletSDK.changeChain(
                chainId = newChainId,
                rpcEndpoint = newRPC
            ))
        }
        val result = future.get()

        if (result == "done") {
            this.chainId = hexToBigInteger(chainId)
            webView.post {
                webView.evaluateJavascript("javascript:window.ethereum._triggerChainChanged('${chainId}')", null)
            }
            return chainId
        } else {
            throw Exception("Failed to switch chain")
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

    private fun bigIntegerToHex(bigInteger: BigInteger): String {
        return "0x" + bigInteger.toString(16)
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
