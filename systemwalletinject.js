if (typeof window.ethereum !== 'undefined') {
    window.ethereum
} else {
    
    window.ethereum = {
        isMetaMask: false,
        selectedAddress: null,
        chainId: 1,
        enable: function() {
            return new Promise(function(resolve, reject) {
              	var addr = window.AndroidEthereum.enableWallet()
                selectedAddress = addr
                isConnected = true
                resolve([addr])
            })
        },
        isConnected: false,
        request: function(request) {
            console.log("Request: ", request)
            return new Promise(function(resolve, reject) {
                console.log("Method dude: ", request.method, " Params dd: ", JSON.stringify(request.params))
                if (request.method == 'eth_requestAccounts') {
                    var addr = window.AndroidEthereum.enableWallet()
                    console.log("Request Accounts: ", addr)
                    selectedAddress = addr
                    isConnected = true
                    resolve([addr])
                } else if (request.method == 'eth_accounts') {
                    var addr = window.AndroidEthereum.getAddress()
                    selectedAddress = addr
                    resolve([addr])
                } else if (request.method == 'eth_sendTransaction') {
                    var tx = window.AndroidEthereum.signTransaction(JSON.stringify(request.params[0]))
                    resolve(tx)
                } else if (request.method == 'eth_sign') {
                    var sig = window.AndroidEthereum.signMessage(request.params[1], request.method)
                    resolve(sig)
                } else if (request.method == 'personal_sign') {
                    var sig = window.AndroidEthereum.signMessage(request.params[0], request.method)
                    resolve(sig)
                } else if (request.method == 'eth_signTypedData') {
                    var sig = window.AndroidEthereum.signTypedData(JSON.stringify(request.params), request.method)
                    resolve(sig)
                } else if (request.method == 'eth_signTypedData_v3') {
                    var sig = window.AndroidEthereum.signTypedData(JSON.stringify(request.params), request.method)
                    resolve(sig)
                } else if (request.method == 'eth_signTypedData_v4') {
                    var sig = window.AndroidEthereum.signTypedData(JSON.stringify(request.params), request.method)
                    resolve(sig)
                } else if (request.method == 'eth_chainId') {
                    resolve(1)
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
                } else {
                    console.log("Method: ", request.method, " Params: ", JSON.stringify(request.params))
                    reject(new Error("Unsupported method: ", request.method))
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
                callback(1)
            } else if (event == 'networkChanged') {
                callback(1)
            } else if (event == 'connect') {
                (function my_func() {
                    if(window.ethereum.isConnected == true) {
                        callback({
                            chainId: 1
                        })
                        return
                    }
                    setTimeout( my_func, 5000 );
                })();
                
            }
        }
    }
}
