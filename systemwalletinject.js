if (window.ethereum) {
    console.log('Ethereum provider is already defined.');
} else {
    window.ethereum = {
        isMetaMask: true, // Assuming you want to mimic MetaMask functionality
        selectedAddress: null,
        chainId: 'CURRENT_CHAIN_ID', // Replace with the actual chain ID in hexadecimal format
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
        isConnectedVar: false,
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
                    this.chainId = request.params[0].chainId;
                    return await window.AndroidEthereum.switchChain(this.chainId);
                case 'eth_accounts':
                    return [this.selectedAddress];
                case 'eth_sendTransaction':
                    if (!request.params || !request.params[0]) {
                        throw new Error('Invalid input parameters');
                    }
                    return await window.AndroidEthereum.signTransaction(JSON.stringify(request.params[0]));
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
                default:
                    throw new Error(`Unsupported method: ${request.method}`);
            }
        },
        on: function(event, callback) {
            console.log("Subscribed to event:", event);
            // You should implement a mechanism in your Android code to trigger these callbacks
            // when the relevant events occur. This could be done by invoking JavaScript from
            // Android when the event happens.
            // ...
        }
    };

    // Trigger the 'connect' event if already connected
    if (window.ethereum.isConnected()) {
        window.ethereum.on('connect', function(info) {
            console.log('Ethereum provider connected with chainId:', info.chainId);
        });
    }
}
