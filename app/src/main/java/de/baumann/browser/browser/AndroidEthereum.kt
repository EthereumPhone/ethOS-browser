package de.baumann.browser.browser

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import de.baumann.browser.R
import org.json.JSONObject
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.http.HttpService
import java.lang.Long.parseLong
import java.math.BigInteger
import java.util.concurrent.CompletableFuture


class AndroidEthereum(
    private val context: Context,
    private val webView: WebView
) {
    private var enabled = false
    private val chainRPC = "https://cloudflare-eth.com"
    private val walletSDK = WalletSDK(
        context = context,
        web3RPC = chainRPC
    )

    // FrameLayout from activity_main.xml
    var view = (context as Activity).findViewById<CoordinatorLayout>(R.id.main_layout)

    val web3j = Web3j.build(HttpService(chainRPC))
    @JavascriptInterface
    fun getAddress(): String {
        if (enabled) {
            return walletSDK.getAddress()
        } else {
            return "0"
        }
    }

    @JavascriptInterface
    fun signTransaction(
        transaction: String
    ): String {
        if (enabled) {
            val txData = JSONObject(transaction)
            return walletSDK.sendTransaction(
                to = txData.getString("to"),
                value =  hexToBigInteger(txData.getString("value")),
                data = if (txData.has("data")) txData.getString("data") else "",
                gasAmount = hexToBigInteger(txData.getString("gas")),
                gasPriceVAL = if (txData.has("gasPrice")) hexToBigInteger(txData.getString("gasPrice")) else null,
            ).get()
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
            walletSDK.signMessage(message, true).get()
        } else {
            walletSDK.signMessage(message, false).get()
        }
    }

    @JavascriptInterface
    fun signTypedData(
        typedData: String
    ): String {
        if (!enabled) {
            return "0"
        }
        return walletSDK.signMessage(typedData, false).get()
    }

    @JavascriptInterface
    fun getBlocknumber(): String {
        if (!enabled) {
            return "0"
        }
        return web3j.ethBlockNumber().send().blockNumber.toString()
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
}
