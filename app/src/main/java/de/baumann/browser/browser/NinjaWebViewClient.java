package de.baumann.browser.browser;

import static androidx.constraintlayout.motion.utils.Oscillator.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Message;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.ethereumphone.walletsdk.WalletSDK;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.baumann.browser.R;
import de.baumann.browser.database.FaviconHelper;
import de.baumann.browser.database.Record;
import de.baumann.browser.database.RecordAction;
import de.baumann.browser.unit.BrowserUnit;
import de.baumann.browser.unit.HelperUnit;
import de.baumann.browser.unit.RecordUnit;
import de.baumann.browser.view.NinjaWebView;
import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

public class NinjaWebViewClient extends WebViewClient {

    private final NinjaWebView ninjaWebView;
    private final Context context;
    private final SharedPreferences sp;
    private final AdBlock adBlock;

    private final WalletSDK walletSDK;

    private final AndroidEthereum mAndroidEthereum;


    public NinjaWebViewClient(NinjaWebView ninjaWebView, AndroidEthereum androidEthereum) {
        super();
        this.ninjaWebView = ninjaWebView;
        this.context = ninjaWebView.getContext();
        this.sp = PreferenceManager.getDefaultSharedPreferences(context);
        this.adBlock = new AdBlock(this.context);
        mAndroidEthereum = androidEthereum;

        walletSDK = new WalletSDK(context,
                Web3j.build(new HttpService("https://cloudflare-eth.com"))
                );


    }



    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        ninjaWebView.isBackPressed = false;

        if (ninjaWebView.isForeground()) ninjaWebView.invalidate();
        else ninjaWebView.postInvalidate();

        if (sp.getBoolean("onPageFinished", false))
            view.evaluateJavascript(Objects.requireNonNull(sp.getString("sp_onPageFinished", "")), null);

        if (ninjaWebView.isSaveData())
            view.evaluateJavascript("var links=document.getElementsByTagName('video'); for(let i=0;i<links.length;i++){links[i].pause()};", null);

        if (ninjaWebView.isHistory()) {
            RecordAction action = new RecordAction(ninjaWebView.getContext());
            action.open(true);
            if (action.checkUrl(ninjaWebView.getUrl(), RecordUnit.TABLE_HISTORY)) action.deleteURL(ninjaWebView.getUrl(), RecordUnit.TABLE_HISTORY);
            action.addHistory(new Record(ninjaWebView.getTitle(), ninjaWebView.getUrl(), System.currentTimeMillis(), 0, 0, ninjaWebView.isDesktopMode(), false, 0));
            action.close();
        }


    }

    @Override
    public void onReceivedError(WebView webview, WebResourceRequest request, WebResourceError error) {
        Context context = webview.getContext();
        String description = error.getDescription().toString();
        String failingUrl = request.getUrl().toString();
        String urlToLoad = sp.getString("urlToLoad", "");
        String htmlData = NinjaWebView.getErrorHTML(context, description, urlToLoad);
        if (urlToLoad.equals(failingUrl)) {
            webview.loadDataWithBaseURL(null, htmlData, "","",failingUrl);
            webview.invalidate();
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {

        ninjaWebView.setStopped(false);
        ninjaWebView.resetFavicon();

        super.onPageStarted(view, url, favicon);

        if (sp.getBoolean("onPageStarted", false))
            view.evaluateJavascript(Objects.requireNonNull(sp.getString("sp_onPageStarted", "")), null);

        if (ninjaWebView.isFingerPrintProtection()) {
            //Block WebRTC requests which can reveal local IP address
            //Tested with https://diafygi.github.io/webrtc-ips/
            view.evaluateJavascript("['createOffer', 'createAnswer','setLocalDescription', 'setRemoteDescription'].forEach(function(method) {\n" +
                    "    webkitRTCPeerConnection.prototype[method] = function() {\n" +
                    "      console.log('webRTC snoop');\n" +
                    "      return null;\n" +
                    "    };\n" +
                    "  });", null);

            //Prevent canvas fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "Canvas Fingerprint Defender", Firefox plugin, Version 0.1.9, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("\n" +
                    "  const toBlob = HTMLCanvasElement.prototype.toBlob;\n" +
                    "  const toDataURL = HTMLCanvasElement.prototype.toDataURL;\n" +
                    "  const getImageData = CanvasRenderingContext2D.prototype.getImageData;\n" +
                    "  //\n" +
                    "  var noisify = function (canvas, context) {\n" +
                    "    if (context) {\n" +
                    "      const shift = {\n" +
                    "        'r': Math.floor(Math.random() * 10) - 5,\n" +
                    "        'g': Math.floor(Math.random() * 10) - 5,\n" +
                    "        'b': Math.floor(Math.random() * 10) - 5,\n" +
                    "        'a': Math.floor(Math.random() * 10) - 5\n" +
                    "      };\n" +
                    "      //\n" +
                    "      const width = canvas.width;\n" +
                    "      const height = canvas.height;\n" +
                    "      if (width && height) {\n" +
                    "        const imageData = getImageData.apply(context, [0, 0, width, height]);\n" +
                    "        for (let i = 0; i < height; i++) {\n" +
                    "          for (let j = 0; j < width; j++) {\n" +
                    "            const n = ((i * (width * 4)) + (j * 4));\n" +
                    "            imageData.data[n + 0] = imageData.data[n + 0] + shift.r;\n" +
                    "            imageData.data[n + 1] = imageData.data[n + 1] + shift.g;\n" +
                    "            imageData.data[n + 2] = imageData.data[n + 2] + shift.b;\n" +
                    "            imageData.data[n + 3] = imageData.data[n + 3] + shift.a;\n" +
                    "          }\n" +
                    "        }\n" +
                    "        //\n" +
                    "        window.top.postMessage(\"canvas-fingerprint-defender-alert\", '*');\n" +
                    "        context.putImageData(imageData, 0, 0); \n" +
                    "      }\n" +
                    "    }\n" +
                    "  };\n" +
                    "  //\n" +
                    "  Object.defineProperty(HTMLCanvasElement.prototype, \"toBlob\", {\n" +
                    "    \"value\": function () {\n" +
                    "      noisify(this, this.getContext(\"2d\"));\n" +
                    "      return toBlob.apply(this, arguments);\n" +
                    "    }\n" +
                    "  });\n" +
                    "  //\n" +
                    "  Object.defineProperty(HTMLCanvasElement.prototype, \"toDataURL\", {\n" +
                    "    \"value\": function () {\n" +
                    "      noisify(this, this.getContext(\"2d\"));\n" +
                    "      return toDataURL.apply(this, arguments);\n" +
                    "    }\n" +
                    "  });\n" +
                    "  //\n" +
                    "  Object.defineProperty(CanvasRenderingContext2D.prototype, \"getImageData\", {\n" +
                    "    \"value\": function () {\n" +
                    "      noisify(this.canvas, this);\n" +
                    "      return getImageData.apply(this, arguments);\n" +
                    "    }\n" +
                    "  });", null);

            //Prevent WebGL fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "WebGL Fingerprint Defender", Firefox plugin, Version 0.1.5, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("\n" +
                    "  var config = {\n" +
                    "    \"random\": {\n" +
                    "      \"value\": function () {\n" +
                    "        return Math.random();\n" +
                    "      },\n" +
                    "      \"item\": function (e) {\n" +
                    "        var rand = e.length * config.random.value();\n" +
                    "        return e[Math.floor(rand)];\n" +
                    "      },\n" +
                    "      \"number\": function (power) {\n" +
                    "        var tmp = [];\n" +
                    "        for (var i = 0; i < power.length; i++) {\n" +
                    "          tmp.push(Math.pow(2, power[i]));\n" +
                    "        }\n" +
                    "        /*  */\n" +
                    "        return config.random.item(tmp);\n" +
                    "      },\n" +
                    "      \"int\": function (power) {\n" +
                    "        var tmp = [];\n" +
                    "        for (var i = 0; i < power.length; i++) {\n" +
                    "          var n = Math.pow(2, power[i]);\n" +
                    "          tmp.push(new Int32Array([n, n]));\n" +
                    "        }\n" +
                    "        /*  */\n" +
                    "        return config.random.item(tmp);\n" +
                    "      },\n" +
                    "      \"float\": function (power) {\n" +
                    "        var tmp = [];\n" +
                    "        for (var i = 0; i < power.length; i++) {\n" +
                    "          var n = Math.pow(2, power[i]);\n" +
                    "          tmp.push(new Float32Array([1, n]));\n" +
                    "        }\n" +
                    "        /*  */\n" +
                    "        return config.random.item(tmp);\n" +
                    "      }\n" +
                    "    },\n" +
                    "    \"spoof\": {\n" +
                    "      \"webgl\": {\n" +
                    "        \"buffer\": function (target) {\n" +
                    "          var proto = target.prototype ? target.prototype : target.__proto__;\n" +
                    "          const bufferData = proto.bufferData;\n" +
                    "          Object.defineProperty(proto, \"bufferData\", {\n" +
                    "            \"value\": function () {\n" +
                    "              var index = Math.floor(config.random.value() * arguments[1].length);\n" +
                    "              var noise = arguments[1][index] !== undefined ? 0.1 * config.random.value() * arguments[1][index] : 0;\n" +
                    "              //\n" +
                    "              arguments[1][index] = arguments[1][index] + noise;\n" +
                    "              window.top.postMessage(\"webgl-fingerprint-defender-alert\", '*');\n" +
                    "              //\n" +
                    "              return bufferData.apply(this, arguments);\n" +
                    "            }\n" +
                    "          });\n" +
                    "        },\n" +
                    "        \"parameter\": function (target) {\n" +
                    "          var proto = target.prototype ? target.prototype : target.__proto__;\n" +
                    "          const getParameter = proto.getParameter;\n" +
                    "          Object.defineProperty(proto, \"getParameter\", {\n" +
                    "            \"value\": function () {\n" +
                    "              window.top.postMessage(\"webgl-fingerprint-defender-alert\", '*');\n" +
                    "              //\n" +
                    "              if (arguments[0] === 3415) return 0;\n" +
                    "              else if (arguments[0] === 3414) return 24;\n" +
                    "              else if (arguments[0] === 36348) return 30;\n" +
                    "              else if (arguments[0] === 7936) return \"WebKit\";\n" +
                    "              else if (arguments[0] === 37445) return \"Google Inc.\";\n" +
                    "              else if (arguments[0] === 7937) return \"WebKit WebGL\";\n" +
                    "              else if (arguments[0] === 3379) return config.random.number([14, 15]);\n" +
                    "              else if (arguments[0] === 36347) return config.random.number([12, 13]);\n" +
                    "              else if (arguments[0] === 34076) return config.random.number([14, 15]);\n" +
                    "              else if (arguments[0] === 34024) return config.random.number([14, 15]);\n" +
                    "              else if (arguments[0] === 3386) return config.random.int([13, 14, 15]);\n" +
                    "              else if (arguments[0] === 3413) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 3412) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 3411) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 3410) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 34047) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 34930) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 34921) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 35660) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 35661) return config.random.number([4, 5, 6, 7, 8]);\n" +
                    "              else if (arguments[0] === 36349) return config.random.number([10, 11, 12, 13]);\n" +
                    "              else if (arguments[0] === 33902) return config.random.float([0, 10, 11, 12, 13]);\n" +
                    "              else if (arguments[0] === 33901) return config.random.float([0, 10, 11, 12, 13]);\n" +
                    "              else if (arguments[0] === 37446) return config.random.item([\"Graphics\", \"HD Graphics\", \"Intel(R) HD Graphics\"]);\n" +
                    "              else if (arguments[0] === 7938) return config.random.item([\"WebGL 1.0\", \"WebGL 1.0 (OpenGL)\", \"WebGL 1.0 (OpenGL Chromium)\"]);\n" +
                    "              else if (arguments[0] === 35724) return config.random.item([\"WebGL\", \"WebGL GLSL\", \"WebGL GLSL ES\", \"WebGL GLSL ES (OpenGL Chromium\"]);\n" +
                    "              //\n" +
                    "              return getParameter.apply(this, arguments);\n" +
                    "            }\n" +
                    "          });\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  };\n" +
                    "  //\n" +
                    "  config.spoof.webgl.buffer(WebGLRenderingContext);\n" +
                    "  config.spoof.webgl.buffer(WebGL2RenderingContext);\n" +
                    "  config.spoof.webgl.parameter(WebGLRenderingContext);\n" +
                    "  config.spoof.webgl.parameter(WebGL2RenderingContext);", null);

            //Prevent AudioContext fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "AudioContext Fingerprint Defender", Firefox plugin, Version 0.1.6, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("\n" +
                    "    const context = {\n" +
                    "    \"BUFFER\": null,\n" +
                    "    \"getChannelData\": function (e) {\n" +
                    "      const getChannelData = e.prototype.getChannelData;\n" +
                    "      Object.defineProperty(e.prototype, \"getChannelData\", {\n" +
                    "        \"value\": function () {\n" +
                    "          const results_1 = getChannelData.apply(this, arguments);\n" +
                    "          if (context.BUFFER !== results_1) {\n" +
                    "            context.BUFFER = results_1;\n" +
                    "            for (var i = 0; i < results_1.length; i += 100) {\n" +
                    "              let index = Math.floor(Math.random() * i);\n" +
                    "              results_1[index] = results_1[index] + Math.random() * 0.0000001;\n" +
                    "            }\n" +
                    "          }\n" +
                    "          //\n" +
                    "          return results_1;\n" +
                    "        }\n" +
                    "      });\n" +
                    "    },\n" +
                    "    \"createAnalyser\": function (e) {\n" +
                    "      const createAnalyser = e.prototype.__proto__.createAnalyser;\n" +
                    "      Object.defineProperty(e.prototype.__proto__, \"createAnalyser\", {\n" +
                    "        \"value\": function () {\n" +
                    "          const results_2 = createAnalyser.apply(this, arguments);\n" +
                    "          const getFloatFrequencyData = results_2.__proto__.getFloatFrequencyData;\n" +
                    "          Object.defineProperty(results_2.__proto__, \"getFloatFrequencyData\", {\n" +
                    "            \"value\": function () {\n" +
                    "              const results_3 = getFloatFrequencyData.apply(this, arguments);\n" +
                    "              for (var i = 0; i < arguments[0].length; i += 100) {\n" +
                    "                let index = Math.floor(Math.random() * i);\n" +
                    "                arguments[0][index] = arguments[0][index] + Math.random() * 0.1;\n" +
                    "              }\n" +
                    "              //\n" +
                    "              return results_3;\n" +
                    "            }\n" +
                    "          });\n" +
                    "          //\n" +
                    "          return results_2;\n" +
                    "        }\n" +
                    "      });\n" +
                    "    }\n" +
                    "  };\n" +
                    "  //\n" +
                    "  context.getChannelData(AudioBuffer);\n" +
                    "  context.createAnalyser(AudioContext);\n" +
                    "  context.getChannelData(OfflineAudioContext);\n" +
                    "  context.createAnalyser(OfflineAudioContext);  ", null);

            //Prevent Font fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "Font Fingerprint Defender", Firefox plugin, Version 0.1.3, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.



            view.evaluateJavascript("\n" +
                    "  var rand = {\n" +
                    "    \"noise\": function () {\n" +
                    "      var SIGN = Math.random() < Math.random() ? -1 : 1;\n" +
                    "      return Math.floor(Math.random() + SIGN * Math.random());\n" +
                    "    },\n" +
                    "    \"sign\": function () {\n" +
                    "      const tmp = [-1, -1, -1, -1, -1, -1, +1, -1, -1, -1];\n" +
                    "      const index = Math.floor(Math.random() * tmp.length);\n" +
                    "      return tmp[index];\n" +
                    "    }\n" +
                    "  };\n" +
                    "  //\n" +
                    "  Object.defineProperty(HTMLElement.prototype, \"offsetHeight\", {\n" +
                    "    get () {\n" +
                    "      const height = Math.floor(this.getBoundingClientRect().height);\n" +
                    "      const valid = height && rand.sign() === 1;\n" +
                    "      const result = valid ? height + rand.noise() : height;\n" +
                    "      //\n" +
                    "      if (valid && result !== height) {\n" +
                    "        window.top.postMessage(\"font-fingerprint-defender-alert\", '*');\n" +
                    "      }\n" +
                    "      //\n" +
                    "      return result;\n" +
                    "    }\n" +
                    "  });\n" +
                    "  //\n" +
                    "  Object.defineProperty(HTMLElement.prototype, \"offsetWidth\", {\n" +
                    "    get () {\n" +
                    "      const width = Math.floor(this.getBoundingClientRect().width);\n" +
                    "      const valid = width && rand.sign() === 1;\n" +
                    "      const result = valid ? width + rand.noise() : width;\n" +
                    "      //\n" +
                    "      if (valid && result !== width) {\n" +
                    "        window.top.postMessage(\"font-fingerprint-defender-alert\", '*');\n" +
                    "      }\n" +
                    "      //\n" +
                    "      return result;\n" +
                    "    }\n" +
                    "  });", null);

            //Spoof screen resolution, color depth: set values like in Tor browser, random values for device memory, hardwareConcurrency, remove battery, network connection, keyboard, media devices info, prevent sendBeacon

            view.evaluateJavascript("" +
                    "Object.defineProperty(window, 'devicePixelRatio',{value:1});" +
                    "Object.defineProperty(window.screen, 'width',{value:1000});" +
                    "Object.defineProperty(window.screen, 'availWidth',{value:1000});" +
                    "Object.defineProperty(window.screen, 'height',{value:900});" +
                    "Object.defineProperty(window.screen, 'availHeight',{value:900});" +
                    "Object.defineProperty(window.screen, 'colorDepth',{value:24});" +
                    "Object.defineProperty(window, 'outerWidth',{value:1000});" +
                    "Object.defineProperty(window, 'outerHeight',{value:900});" +
                    "Object.defineProperty(window, 'innerWidth',{value:1000});" +
                    "Object.defineProperty(window, 'innerHeight',{value:900});" +
                    "Object.defineProperty(navigator, 'getBattery',{value:function(){}});" +
                    "const ram=Math.pow(2,Math.floor(Math.random() * 4));Object.defineProperty(navigator, 'deviceMemory',{value:ram});" +
                    "const hw=Math.pow(2,Math.floor(Math.random() * 4));Object.defineProperty(navigator, 'hardwareConcurrency',{value:hw});" +
                    "Object.defineProperty(navigator, 'connection',{value:null});" +
                    "Object.defineProperty(navigator, 'keyboard',{value:null});" +
                    "Object.defineProperty(navigator, 'sendBeacon',{value:null});", null);

            if (!ninjaWebView.isCamera())
                view.evaluateJavascript("" + "Object.defineProperty(navigator, 'mediaDevices',{value:null});", null);
        }
        // Inject EIP-1193 provider
        String systemWalletJs = "if (window.ethereum) {\n" +
                "    console.log('Ethereum provider is already defined.');\n" +
                "} else {\n" +
                "    window.ethereum = {\n" +
                "        isMetaMask: true, // Assuming you want to mimic MetaMask functionality\n" +
                "        selectedAddress: null,\n" +
                "        chainId: 'CURRENT_CHAIN_ID', // Replace with the actual chain ID in hexadecimal format\n" +
                "        _chainChangedListeners: [],\n" +
                "        _triggerConnectEvent: function() {\n" +
                "            if (typeof this.onConnect === 'function') {\n" +
                "                this.onConnect({ chainId: this.chainId });\n" +
                "            }\n" +
                "            this.enable();\n" +
                "        },        \n" +
                "        enable: async function() {\n" +
                "            console.log(\"ENABLE\")\n" +
                "            if (this.selectedAddress) {\n" +
                "                return [this.selectedAddress];\n" +
                "            }\n" +
                "            try {\n" +
                "                const addr = await window.AndroidEthereum.enableWallet();\n" +
                "                this.selectedAddress = addr;\n" +
                "                this.isConnectedVar = true;\n" +
                "                return [addr];\n" +
                "            } catch (error) {\n" +
                "                throw new Error('User denied account access');\n" +
                "                console.log(\"ERROR: \" + error)\n" +
                "            }\n" +
                "        },\n" +
                "        isConnectedVar: REPLACE_IS_CONNECTED_VAR,\n" +
                "        isConnected: function() {\n" +
                "            return this.isConnectedVar;\n" +
                "        },\n" +
                "        request: async function(request) {\n" +
                "            console.log(\"NEW REQUEST: \" + JSON.stringify(request))\n" +
                "            switch (request.method) {\n" +
                "                case 'eth_requestAccounts':\n" +
                "                    return this.enable();\n" +
                "                case 'wallet_switchEthereumChain':\n" +
                "                    if (!request.params || !request.params[0].chainId) {\n" +
                "                        throw new Error('Invalid input parameters');\n" +
                "                    }\n" +
                "                    try {\n" +
                "                        const result = await window.AndroidEthereum.switchChain(request.params[0].chainId);\n" +
                "                        this.chainId = result; // Update the chainId only after successful switch\n" +
                "                        return null;\n" +
                "                    } catch (error) {\n" +
                "                        throw new Error(`Failed to switch chain: ${error.message}`);\n" +
                "                    }\n" +
                "                case 'eth_accounts':\n" +
                "                    return [this.selectedAddress];\n" +
                "                case 'eth_sendTransaction':\n" +
                "                    if (!request.params || !request.params[0]) {\n" +
                "                        throw new Error('Invalid input parameters');\n" +
                "                    }\n" +
                "                    const result = await window.AndroidEthereum.signTransaction(JSON.stringify(request.params[0]));\n" +
                "                    if (result === \"decline\") {\n" +
                "                        throw new Error('User declined the transaction');\n" +
                "                    }\n" +
                "                    return result;\n" +
                "                case 'eth_sign':\n" +
                "                    return window.AndroidEthereum.signMessage(request.params[1], 'eth_sign');\n" +
                "                case 'personal_sign':\n" +
                "                    return window.AndroidEthereum.signMessage(request.params[0], 'personal_sign');\n" +
                "                case 'eth_signTypedData':\n" +
                "                case 'eth_signTypedData_v3':\n" +
                "                case 'eth_signTypedData_v4':\n" +
                "                    return await window.AndroidEthereum.signTypedData(JSON.stringify(request.params[1]));\n" +
                "                case 'eth_chainId':\n" +
                "                    return this.chainId;\n" +
                "                case 'net_version':\n" +
                "                    return '1'; // Replace with the correct network version if needed\n" +
                "                case 'eth_blockNumber':\n" +
                "                    return window.AndroidEthereum.getBlockNumber();\n" +
                "                case 'eth_call':\n" +
                "                    if (!request.params || !request.params[0]) {\n" +
                "                        throw new Error('Invalid input parameters');\n" +
                "                    }\n" +
                "                    return window.AndroidEthereum.ethCall(JSON.stringify(request.params[0]));\n" +
                "                case 'eth_estimateGas':\n" +
                "                    if (!request.params || !request.params[0]) {\n" +
                "                        throw new Error('Invalid input parameters');\n" +
                "                    }\n" +
                "                    return window.AndroidEthereum.estimateGas(JSON.stringify(request.params[0]));\n" +
                "                case 'eth_getTransactionByHash':\n" +
                "                    var txByHash = await window.AndroidEthereum.getTransactionByHash(request.params[0]);\n" +
                "                    return JSON.parse(txByHash)\n" +
                "                case 'eth_getTransactionReceipt':\n" +
                "                    var txReceipt = await window.AndroidEthereum.getTransactionReceipt(request.params[0]);\n" +
                "                    return JSON.parse(txReceipt)\n" +
                "                case 'eth_getBlockByNumber':\n" +
                "                    return window.AndroidEthereum.getBlockByNumber(request.params[0], request.params[1]);\n" +
                "                case 'wallet_addEthereumChain':\n" +
                "                    if (!request.params || !request.params[0]) {\n" +
                "                        throw new Error('Invalid input parameters');\n" +
                "                    }\n" +
                "                    this.chainId = request.params[0].chainId;\n" +
                "                    return await window.AndroidEthereum.addChain(JSON.stringify(request.params[0]));\n" +
                "                case 'wallet_requestPermissions':\n" +
                "                    if (!request.params || !request.params[0]) {\n" +
                "                        throw new Error('Invalid input parameters');\n" +
                "                    }\n" +
                "                    \n" +
                "                    // Assuming that the primary permission being requested is to access accounts.\n" +
                "                    // You can expand this to handle other permissions as per your application's requirements.\n" +
                "                    const permissions = request.params[0];\n" +
                "                    if (permissions && permissions.eth_accounts) {\n" +
                "                        // Here, you'd typically check if the user has already granted this permission.\n" +
                "                        // For simplicity, assuming the user grants permission:\n" +
                "                        await this.enable(); // Replace with your method to get accounts\n" +
                "                        return [{ invoker: window.location.hostname, data: { eth_accounts: {} } }];\n" +
                "                    } else {\n" +
                "                        // Handle other permissions or throw an error for unsupported permissions\n" +
                "                        throw new Error('Unsupported permission requested');\n" +
                "                    }\n" +
                "                case 'eth_getTransactionCount':\n" +
                "                    if (!request.params || !request.params[0]) {\n" +
                "                        throw new Error('Invalid input parameters');\n" +
                "                    }\n" +
                "                    try {\n" +
                "                        // Assuming `getTransactionCount` is a method in your Kotlin backend\n" +
                "                        // that returns the transaction count for the given address\n" +
                "                        var txCount = await window.AndroidEthereum.getTransactionCount(request.params[0]);\n" +
                "                        return txCount; // Make sure txCount is formatted as a hexadecimal string\n" +
                "                    } catch (error) {\n" +
                "                        throw new Error(`Failed to get transaction count: ${error.message}`);\n" +
                "                    }\n" +
                "                case 'eth_getBalance':\n" +
                "                    if (!request.params || !request.params[0]) {\n" +
                "                        throw new Error('Invalid input parameters');\n" +
                "                    }\n" +
                "                    try {\n" +
                "                        var balance = await window.AndroidEthereum.getBalance(request.params[0]);\n" +
                "                        return balance; // Balance is expected to be returned as a hexadecimal string\n" +
                "                    } catch (error) {\n" +
                "                        throw new Error(`Failed to get balance: ${error.message}`);\n" +
                "                    }\n" +
                "                    \n" +
                "                default:\n" +
                "                    throw new Error(`Unsupported method: ${request.method}`);\n" +
                "            }\n" +
                "        },\n" +
                "        on: function(event, listener) {\n" +
                "            console.log(\"Subscribed to event:\", event);\n" +
                "            // You should implement a mechanism in your Android code to trigger these callbacks\n" +
                "            // when the relevant events occur. This could be done by invoking JavaScript from\n" +
                "            // Android when the event happens.\n" +
                "            // ...\n" +
                "            if (event === 'chainChanged') {\n" +
                "                this._chainChangedListeners.push(listener);\n" +
                "            }\n" +
                "        },\n" +
                "        _triggerChainChanged: function(chainId) {\n" +
                "            this._chainChangedListeners.forEach(listener => listener(chainId));\n" +
                "        },\n" +
                "    };\n" +
                "\n" +
                "    // Trigger the 'connect' event if already connected\n" +
                "    if (window.ethereum.isConnected()) {\n" +
                "        window.ethereum.on('connect', function(info) {\n" +
                "            console.log('Ethereum provider connected with chainId:', info.chainId);\n" +
                "        });\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "if (window.ethereum.isConnected()) {\n" +
                "    window.ethereum._triggerConnectEvent(); // Add this function in the ethereum object\n" +
                "}\n" +
                "\n" +
                "(function() {\n" +
                "    const originalRequest = ethereum.request;\n" +
                "\n" +
                "    ethereum.request = async function(request) {\n" +
                "        console.log('MetaMask Request:', request);\n" +
                "        try {\n" +
                "            const response = await originalRequest.apply(ethereum, [request]);\n" +
                "            console.log('MetaMask Response:', response);\n" +
                "            console.log('Current chainID: ', window.ethereum.chainId)\n" +
                "            return response;\n" +
                "        } catch (error) {\n" +
                "            console.error('MetaMask Error:', error);\n" +
                "            throw error; // Re-throw the error so that the original behavior is preserved\n" +
                "        }\n" +
                "    };\n" +
                "\n" +
                "    console.log('MetaMask request logging is enabled.');\n" +
                "})();\n";
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        Continuation<Integer> continuation = new Continuation<Integer>() {

            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object o) {
                if (o instanceof Result.Failure)
                    completableFuture.completeExceptionally((((Result.Failure) o).exception));
                else
                    completableFuture.complete((Integer) o);
            }
        };

        walletSDK.getChainId(continuation);

        String currChainId = null;
        try {
            currChainId = toHexString(completableFuture.get());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        systemWalletJs = systemWalletJs.replace("CURRENT_CHAIN_ID", currChainId);
        MyHashMapManager hashMapManager = new MyHashMapManager(context);
        boolean isConnected = false;
        try {
            isConnected = hashMapManager.getHashMap().get(getDomainName(ninjaWebView.getUrl()));
        } catch (NullPointerException ignored) {}

        if (isConnected) {
            systemWalletJs = systemWalletJs.replace("REPLACE_IS_CONNECTED_VAR", "true");
        } else {
            systemWalletJs = systemWalletJs.replace("REPLACE_IS_CONNECTED_VAR", "false");
        }
        view.evaluateJavascript(systemWalletJs, null);
    }

    private String getDomainName(String url) {
        Uri uri = Uri.parse(url);
        String domain = uri.getHost();
        return domain != null ? domain : "";
    }

    /**
     * Function to turn Integer into a hex string starting with 0x
     */
    private String toHexString(int i) {
        return "0x" + Integer.toHexString(i);
    }

    @Override
    public void onLoadResource(WebView view, String url) {

        if (sp.getBoolean("onLoadResource", false))
            view.evaluateJavascript(Objects.requireNonNull(sp.getString("sp_onLoadResource", "")), null);

        if (ninjaWebView.isFingerPrintProtection())
            view.evaluateJavascript("var test=document.querySelector(\"a[ping]\"); if(test!==null){test.removeAttribute('ping')};", null);
            //do not allow ping on http only pages (tested with http://tests.caniuse.com)

        if (view.getSettings().getUseWideViewPort() && (view.getWidth() < 1300))
            view.evaluateJavascript("document.querySelector('meta[name=\"viewport\"]').setAttribute('content', 'width=1200px');", null);
        //  Client-side detection for GlobalPrivacyControl
        view.evaluateJavascript("if (navigator.globalPrivacyControl === undefined) { Object.defineProperty(navigator, 'globalPrivacyControl', { value: true, writable: false,configurable: false});} else {try { navigator.globalPrivacyControl = true;} catch (e) { console.error('globalPrivacyControl is not writable: ', e); }};", null);
        //  Script taken from:
        //
        //  donotsell.js
        //  DuckDuckGo
        //
        //  Copyright © 2020 DuckDuckGo. All rights reserved.
        //
        //  Licensed under the Apache License, Version 2.0 (the "License");
        //  you may not use this file except in compliance with the License.
        //  You may obtain a copy of the License at
        //
        //  http://www.apache.org/licenses/LICENSE-2.0
        //
        //  Unless required by applicable law or agreed to in writing, software
        //  distributed under the License is distributed on an "AS IS" BASIS,
        //  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        //  See the License for the specific language governing permissions and
        //  limitations under the License.
        //
        view.evaluateJavascript("if (navigator.doNotTrack === null) { Object.defineProperty(navigator, 'doNotTrack', { value: 1, writable: false,configurable: false});} else {try { navigator.doNotTrack = 1;} catch (e) { console.error('doNotTrack is not writable: ', e); }};", null);
        view.evaluateJavascript("if (window.doNotTrack === undefined) { Object.defineProperty(window, 'doNotTrack', { value: 1, writable: false,configurable: false});} else {try { window.doNotTrack = 1;} catch (e) { console.error('doNotTrack is not writable: ', e); }};", null);
        view.evaluateJavascript("if (navigator.msDoNotTrack === undefined) { Object.defineProperty(navigator, 'msDoNotTrack', { value: 1, writable: false,configurable: false});} else {try { navigator.msDoNotTrack = 1;} catch (e) { console.error('msDoNotTrack is not writable: ', e); }};", null);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        final Uri uri = request.getUrl();
        String url = uri.toString();

        if (ninjaWebView.isBackPressed) return false;
        else {
            // handle the url by implementing your logic
            if (url.startsWith("http://") || url.startsWith("https://")) {
                String urlToLoad = BrowserUnit.redirectURL(ninjaWebView, sp, url);
                this.ninjaWebView.initPreferences(urlToLoad);
                ninjaWebView.loadUrl(urlToLoad);
                return false;

            } else if (url.startsWith("wc:")) {
                mAndroidEthereum.connectToWalletConnect(url);
                return true;
            } else {
                try {
                    Intent intent;
                    if (url.startsWith("intent:")) {
                        intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        intent.addCategory("android.intent.category.BROWSABLE");
                        intent.setComponent(null);
                        intent.setSelector(null);
                    } else {
                        intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    }
                    view.getContext().startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Log.i(TAG, "shouldOverrideUrlLoading Exception:" + e);
                    return true;
                }
            }
        }
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (ninjaWebView.isAdBlock() && adBlock.isAd(request.getUrl().toString()))
            return new WebResourceResponse(
                    BrowserUnit.MIME_TYPE_TEXT_PLAIN,
                    BrowserUnit.URL_ENCODING,
                    new ByteArrayInputStream("".getBytes())
            );
        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onFormResubmission(WebView view, @NonNull final Message doNotResend, final Message resend) {

        View dialogView = View.inflate(context, R.layout.dialog_menu, null);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

        LinearLayout textGroup = dialogView.findViewById(R.id.textGroup);
        TextView menuURL = dialogView.findViewById(R.id.menuURL);
        menuURL.setText(view.getUrl());
        menuURL.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        menuURL.setSingleLine(true);
        menuURL.setMarqueeRepeatLimit(1);
        menuURL.setSelected(true);
        textGroup.setOnClickListener(v -> {
            menuURL.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            menuURL.setSingleLine(true);
            menuURL.setMarqueeRepeatLimit(1);
            menuURL.setSelected(true);
        });
        TextView menuTitle = dialogView.findViewById(R.id.menuTitle);
        menuTitle.setText(HelperUnit.domain(view.getUrl()));
        TextView messageView = dialogView.findViewById(R.id.message);
        messageView.setVisibility(View.VISIBLE);
        messageView.setText(R.string.dialog_content_resubmission);
        FaviconHelper.setFavicon(context, dialogView, null, R.id.menu_icon, R.drawable.icon_alert);
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> resend.sendToTarget());
        builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> doNotResend.sendToTarget());
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setOnCancelListener(d -> doNotResend.sendToTarget());
        HelperUnit.setupDialog(context, dialog);
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    @Override
    public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
        String message;
        switch (error.getPrimaryError()) {
            case SslError.SSL_UNTRUSTED:
                message = "\"Certificate authority is not trusted.\"";
                break;
            case SslError.SSL_EXPIRED:
                message = "\"Certificate has expired.\"";
                break;
            case SslError.SSL_IDMISMATCH:
                message = "\"Certificate Hostname mismatch.\"";
                break;
            case SslError.SSL_NOTYETVALID:
                message = "\"Certificate is not yet valid.\"";
                break;
            case SslError.SSL_DATE_INVALID:
                message = "\"Certificate date is invalid.\"";
                break;
            default:
                message = "\"Certificate is invalid.\"";
                break;
        }
        String text = message + " - " + context.getString(R.string.dialog_content_ssl_error);

        View dialogView = View.inflate(context, R.layout.dialog_menu, null);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

        LinearLayout textGroup = dialogView.findViewById(R.id.textGroup);
        TextView menuURL = dialogView.findViewById(R.id.menuURL);
        menuURL.setText(view.getUrl());
        menuURL.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        menuURL.setSingleLine(true);
        menuURL.setMarqueeRepeatLimit(1);
        menuURL.setSelected(true);
        textGroup.setOnClickListener(v -> {
            menuURL.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            menuURL.setSingleLine(true);
            menuURL.setMarqueeRepeatLimit(1);
            menuURL.setSelected(true);
        });
        TextView menuTitle = dialogView.findViewById(R.id.menuTitle);
        menuTitle.setText(HelperUnit.domain(view.getUrl()));
        TextView messageView = dialogView.findViewById(R.id.message);
        messageView.setVisibility(View.VISIBLE);
        messageView.setText(text);
        FaviconHelper.setFavicon(context, dialogView, null, R.id.menu_icon, R.drawable.icon_alert);
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> handler.proceed());
        builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> handler.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setOnCancelListener(dialog1 -> handler.cancel());
        HelperUnit.setupDialog(context, dialog);
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, @NonNull final HttpAuthHandler handler, String host, String realm) {

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = View.inflate(context, R.layout.dialog_edit, null);

        TextInputLayout editTopLayout = dialogView.findViewById(R.id.editTopLayout);
        editTopLayout.setHint(this.context.getString(R.string.dialog_sign_in_username));
        TextInputLayout editBottomLayout = dialogView.findViewById(R.id.editBottomLayout);
        editBottomLayout.setHint(this.context.getString(R.string.dialog_sign_in_password));
        TextInputEditText editTop = dialogView.findViewById(R.id.editTop);
        TextInputEditText editBottom = dialogView.findViewById(R.id.editBottom);
        editTop.setText("");
        editTop.setHint(this.context.getString(R.string.dialog_sign_in_username));
        editBottom.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editBottom.setText("");
        editBottom.setHint(this.context.getString(R.string.dialog_sign_in_password));

        builder.setView(dialogView);

        LinearLayout textGroupEdit = dialogView.findViewById(R.id.textGroupEdit);
        TextView menuURLEdit = dialogView.findViewById(R.id.menuURLEdit);
        menuURLEdit.setText(view.getUrl());
        menuURLEdit.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        menuURLEdit.setSingleLine(true);
        menuURLEdit.setMarqueeRepeatLimit(1);
        menuURLEdit.setSelected(true);
        textGroupEdit.setOnClickListener(v -> {
            menuURLEdit.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            menuURLEdit.setSingleLine(true);
            menuURLEdit.setMarqueeRepeatLimit(1);
            menuURLEdit.setSelected(true);
        });
        TextView menuTitleEdit = dialogView.findViewById(R.id.menuTitleEdit);
        menuTitleEdit.setText(view.getTitle());
        FaviconHelper.setFavicon(context, dialogView, null, R.id.menu_icon, R.drawable.icon_alert);

        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUnit.setupDialog(context, dialog);
        dialog.setOnCancelListener(dialog1 -> {
            handler.cancel();
            dialog1.cancel();
        });

        Button ib_cancel = dialogView.findViewById(R.id.editCancel);
        ib_cancel.setOnClickListener(v -> {
            HelperUnit.hideSoftKeyboard(editBottom, context);
            dialog.cancel();
        });
        Button ib_ok = dialogView.findViewById(R.id.editOK);
        ib_ok.setOnClickListener(v -> {
            HelperUnit.hideSoftKeyboard(editBottom, context);
            String user = Objects.requireNonNull(editTop.getText()).toString().trim();
            String pass = Objects.requireNonNull(editBottom.getText()).toString().trim();
            handler.proceed(user, pass);
            dialog.cancel();
        });
    }
}