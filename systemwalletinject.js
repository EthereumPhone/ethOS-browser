if (typeof window.ethereum !== 'undefined') {
    window.ethereum
} else {
    
    window.ethereum = {
        isMetaMask: true,
        selectedAddress: null,
        chainId: "CURRENT_CHAIN_ID",
        savedChainChangedCallback: null,
        enable: function() {
            return new Promise(function(resolve, reject) {
                if (window.ethereum.selectedAddress != null) {
                    resolve([window.ethereum.selectedAddress])
                    return
                }
                var addr = window.AndroidEthereum.enableWallet()
                console.log("Request Accounts: ", addr)
                window.ethereum.selectedAddress = addr
                window.ethereum.isConnectedVar = true
                resolve([addr])
            })
        },
        isConnectedVar: REPLACE_IS_CONNECTED_VAR,
        isConnected: function() {
            return window.ethereum.isConnectedVar;
        },
        request: function(request) {
            console.log("Request: ", JSON.stringify(request))
            return new Promise(function(resolve, reject) {
                console.log("Method dude: ", request.method, " Params dd: ", JSON.stringify(request.params))
                if (request.method == 'eth_requestAccounts') {
                    console.log("Selected Address: ", window.ethereum.selectedAddress)
                    if (window.ethereum.selectedAddress != null) {
                        resolve([window.ethereum.selectedAddress])
                        return
                    }
                    var addr = window.AndroidEthereum.enableWallet()
                    console.log("Request Accounts: ", addr)
                    window.ethereum.selectedAddress = addr
                    window.ethereum.isConnectedVar = true
                    resolve([addr])
                } else if (request.method == 'wallet_switchEthereumChain') {
                    var chainId = request.params[0].chainId
                    var chain = window.AndroidEthereum.switchChain(chainId)
                    console.log("Switch Chain: ", chain)
                    window.ethereum.chainId = chain
                    resolve(chain)
                } else if (request.method == 'eth_accounts') {
                    var addr = window.AndroidEthereum.getAddress()
                    selectedAddress = addr
                    resolve([addr])
                } else if (request.method == 'eth_sendTransaction') {
                    console.log("Send Transaction: ", JSON.stringify(request.params))
                    var tx = window.AndroidEthereum.signTransaction(JSON.stringify(request.params[0]))
                    resolve(tx)
                } else if (request.method == 'eth_sign') {
                    var sig = window.AndroidEthereum.signMessage(request.params[1], request.method)
                    if (sig == "declined") {
                        reject(new Error(4001, 'User rejected the request'))
                    }
                    resolve(sig)
                } else if (request.method == 'personal_sign') {
                    var sig = window.AndroidEthereum.signMessage(request.params[0], request.method)
                    resolve(sig)
                } else if (request.method == 'eth_signTypedData') {
                    console.log("Typed Data V1: ", JSON.stringify(request.params[1]))
                    var sig = window.AndroidEthereum.signMessage(JSON.stringify(request.params[1]), request.method)
                    resolve(sig)
                } else if (request.method == 'eth_signTypedData_v3') {
                    console.log("Typed Data: ", JSON.stringify(request.params[1]))
                    var sig = window.AndroidEthereum.signTypedData(JSON.stringify(request.params[1]))
                    resolve(sig)
                } else if (request.method == 'eth_signTypedData_v4') {
                    console.log("Typed Data V4: ", JSON.stringify(request.params[1]))
                    var sig = window.AndroidEthereum.signMessage(JSON.stringify(request.params[1]), "eth_signTypedData")
                    resolve(sig)
                } else if (request.method == 'eth_chainId') {
                    var newestChainId = window.AndroidEthereum.getNewestChainId()
                    resolve(newestChainId)
                } else if (request.method == 'net_version') {
                    resolve(1)
                } else if (request.method == 'eth_blockNumber') {
                    var blocknumber = window.AndroidEthereum.getBlocknumber()
                    resolve(blocknumber)
                } else if (request.method == 'eth_call') {
                    var call = window.AndroidEthereum.ethCall(JSON.stringify(request.params[0]))
                    resolve(call)
                } else if (request.method == 'eth_estimateGas') {
                    var gas = window.AndroidEthereum.estimateGas(JSON.stringify(request.params[0]))
                    resolve(gas)
                } else if (request.method == 'eth_getTransactionByHash') {
                    var jsonStr = window.AndroidEthereum.getTransactionByHash(request.params[0])
                    console.log("Transaction-result: ", jsonStr)
                    if (jsonStr == "0") {
                        resolve({})
                    }
                    resolve(JSON.parse(jsonStr))
                } else if (request.method == 'eth_getTransactionReceipt') {
                    var jsonStr = window.AndroidEthereum.getTransactionReceipt(request.params[0])
                    console.log("Transaction-receipt: ", jsonStr)
                    if (jsonStr == "0") {
                        resolve(null)
                    }
                    resolve(JSON.parse(jsonStr))
                } else if (request.method == 'eth_getBlockByNumber') {
                    var result = window.AndroidEthereum.getBlockByNumber(request.params[0], request.params[1])
                    console.log("Block_RESULT: ", result)
                    resolve(JSON.parse(result))
                } else if (request.method == 'wallet_addEthereumChain') {
                    var chain = window.AndroidEthereum.addChain(JSON.stringify(request.params[0]))
                    console.log("Add Chain: ", request.params)
                    window.ethereum.chainId = chain
                    resolve(chain)
                } else {
                    console.log("Method: ", request.method, " Params: ", JSON.stringify(request.params))
                    reject(new Error("Not cool method: " + JSON.stringify(request)))
                }
            })
        },
        on: function(event, callback) {
            console.log("On Event: ", event)
            if (event == 'accountsChanged') {
                (function refresh_wallet() {
                    if(window.ethereum.selectedAddress != null) {
                        callback([window.ethereum.selectedAddress])
                        return
                    }
                    setTimeout( refresh_wallet, 5000 );
                })();
            } else if (event == 'chainChanged') {
                //callback(1)
                window.ethereum.savedChainChangedCallback = callback
            } else if (event == 'networkChanged') {
                //callback(1)
                //window.ethereum.savedChainChangedCallback = callback
            } else if (event == 'connect') {
                (function my_func() {
                    if(window.ethereum.isConnected() == true) {
                        callback({
                            chainId: window.ethereum.chainId,
                        })
                        return
                    }
                    setTimeout( my_func, 5000 );
                })();
                
            }
        }
    }
}
