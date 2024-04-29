if (window.ethereum) {
    console.log('Ethereum provider is already defined.');
} else {
    window.ethereum = {
        isMetaMask: true, // Assuming you want to mimic MetaMask functionality
        selectedAddress: null,
        chainId: 'CURRENT_CHAIN_ID', // Replace with the actual chain ID in hexadecimal format
        _chainChangedListeners: [],
        _triggerConnectEvent: function() {
            if (typeof this.onConnect === 'function') {
                this.onConnect({ chainId: this.chainId });
            }
            this.enable();
        },        
        enable: async function() {
            console.log("ENABLE")
            if (this.selectedAddress) {
                return [this.selectedAddress];
            }
            try {
                const addr = await window.AndroidEthereum.enableWallet();
                this.selectedAddress = addr;
                this.isConnectedVar = true;
                return [addr];
            } catch (error) {
                throw new Error('User denied account access');
                console.log("ERROR: " + error)
            }
        },
        isConnectedVar: REPLACE_IS_CONNECTED_VAR,
        isConnected: function() {
            return this.isConnectedVar;
        },
        request: async function(request) {
            console.log("NEW REQUEST: " + JSON.stringify(request))
            switch (request.method) {
                case 'eth_requestAccounts':
                    return this.enable();
                case 'wallet_switchEthereumChain':
                    if (!request.params || !request.params[0].chainId) {
                        throw new Error('Invalid input parameters');
                    }
                    try {
                        const result = await window.AndroidEthereum.switchChain(request.params[0].chainId);
                        this.chainId = result; // Update the chainId only after successful switch
                        return null;
                    } catch (error) {
                        throw new Error(`Failed to switch chain: ${error.message}`);
                    }
                case 'eth_accounts':
                    return [this.selectedAddress];
                case 'eth_sendTransaction':
                    if (!request.params || !request.params[0]) {
                        throw new Error('Invalid input parameters');
                    }
                    const result = await window.AndroidEthereum.signTransaction(JSON.stringify(request.params[0]));
                    if (result === "decline") {
                        throw new Error('User declined the transaction');
                    }
                    return result;
                case 'eth_sign':
                    return window.AndroidEthereum.signMessage(request.params[1], 'eth_sign');
                case 'personal_sign':
                    return window.AndroidEthereum.signMessage(request.params[0], 'personal_sign');
                case 'eth_signTypedData':
                case 'eth_signTypedData_v3':
                case 'eth_signTypedData_v4':
                    return await window.AndroidEthereum.signTypedData(JSON.stringify(request.params[1]));
                case 'eth_chainId':
                    return this.chainId;
                case 'net_version':
                    return '1'; // Replace with the correct network version if needed
                case 'eth_blockNumber':
                    return window.AndroidEthereum.getBlockNumber();
                case 'eth_call':
                    if (!request.params || !request.params[0]) {
                        throw new Error('Invalid input parameters');
                    }
                    return window.AndroidEthereum.ethCall(JSON.stringify(request.params[0]));
                case 'eth_estimateGas':
                    if (!request.params || !request.params[0]) {
                        throw new Error('Invalid input parameters');
                    }
                    return window.AndroidEthereum.estimateGas(JSON.stringify(request.params[0]));
                case 'eth_getTransactionByHash':
                    var txByHash = await window.AndroidEthereum.getTransactionByHash(request.params[0]);
                    return JSON.parse(txByHash)
                case 'eth_getTransactionReceipt':
                    var txReceipt = await window.AndroidEthereum.getTransactionReceipt(request.params[0]);
                    return JSON.parse(txReceipt)
                case 'eth_getBlockByNumber':
                    return window.AndroidEthereum.getBlockByNumber(request.params[0], request.params[1]);
                case 'wallet_addEthereumChain':
                    if (!request.params || !request.params[0]) {
                        throw new Error('Invalid input parameters');
                    }
                    this.chainId = request.params[0].chainId;
                    return await window.AndroidEthereum.addChain(JSON.stringify(request.params[0]));
                case 'wallet_requestPermissions':
                    if (!request.params || !request.params[0]) {
                        throw new Error('Invalid input parameters');
                    }
                    
                    // Assuming that the primary permission being requested is to access accounts.
                    // You can expand this to handle other permissions as per your application's requirements.
                    const permissions = request.params[0];
                    if (permissions && permissions.eth_accounts) {
                        // Here, you'd typically check if the user has already granted this permission.
                        // For simplicity, assuming the user grants permission:
                        await this.enable(); // Replace with your method to get accounts
                        return [{ invoker: window.location.hostname, data: { eth_accounts: {} } }];
                    } else {
                        // Handle other permissions or throw an error for unsupported permissions
                        throw new Error('Unsupported permission requested');
                    }
                case 'eth_getTransactionCount':
                    if (!request.params || !request.params[0]) {
                        throw new Error('Invalid input parameters');
                    }
                    try {
                        // Assuming `getTransactionCount` is a method in your Kotlin backend
                        // that returns the transaction count for the given address
                        var txCount = await window.AndroidEthereum.getTransactionCount(request.params[0]);
                        return txCount; // Make sure txCount is formatted as a hexadecimal string
                    } catch (error) {
                        throw new Error(`Failed to get transaction count: ${error.message}`);
                    }
                case 'eth_getBalance':
                    if (!request.params || !request.params[0]) {
                        throw new Error('Invalid input parameters');
                    }
                    try {
                        var balance = await window.AndroidEthereum.getBalance(request.params[0]);
                        return balance; // Balance is expected to be returned as a hexadecimal string
                    } catch (error) {
                        throw new Error(`Failed to get balance: ${error.message}`);
                    }
                    
                default:
                    throw new Error(`Unsupported method: ${request.method}`);
            }
        },
        on: function(event, listener) {
            console.log("Subscribed to event:", event);
            // You should implement a mechanism in your Android code to trigger these callbacks
            // when the relevant events occur. This could be done by invoking JavaScript from
            // Android when the event happens.
            // ...
            if (event === 'chainChanged') {
                this._chainChangedListeners.push(listener);
            }
        },
        _triggerChainChanged: function(chainId) {
            this._chainChangedListeners.forEach(listener => listener(chainId));
        },
    };

    // Trigger the 'connect' event if already connected
    if (window.ethereum.isConnected()) {
        window.ethereum.on('connect', function(info) {
            console.log('Ethereum provider connected with chainId:', info.chainId);
        });
    }
}

if (window.ethereum.isConnected()) {
    window.ethereum._triggerConnectEvent(); // Add this function in the ethereum object
}

(function() {
    const originalRequest = ethereum.request;

    ethereum.request = async function(request) {
        console.log('MetaMask Request:', request);
        try {
            const response = await originalRequest.apply(ethereum, [request]);
            console.log('MetaMask Response:', response);
            console.log('Current chainID: ', window.ethereum.chainId)
            return response;
        } catch (error) {
            console.error('MetaMask Error:', error);
            throw error; // Re-throw the error so that the original behavior is preserved
        }
    };

    console.log('MetaMask request logging is enabled.');
})();
