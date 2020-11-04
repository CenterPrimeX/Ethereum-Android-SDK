package com.centerprime.ethereum_client_sdk;

import android.content.Context;

import com.centerprime.ethereum_client_sdk.util.BalanceUtils;
import com.centerprime.ethereum_client_sdk.util.CenterPrimeUtils;
import com.centerprime.ethereum_client_sdk.util.Const;
import com.centerprime.ethereum_client_sdk.util.Erc20TokenWrapper;
import com.centerprime.ethereum_client_sdk.util.HyperLedgerApi;
import com.centerprime.ethereum_client_sdk.util.SubmitTransactionModel;
import com.centerprime.ethereum_client_sdk.util.Wallet;

import org.spongycastle.util.encoders.Hex;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ChainId;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.NoOpProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.HashMap;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by CenterPrime on 2020/09/19.
 */
public class EthManager {
    private static final EthManager ourInstance = new EthManager();


    /**
     * Web3j Client
     */
    private Web3j web3j;


    private HyperLedgerApi hyperLedgerApi;

    /**
     * Infura node url
     */
    private String mainnetInfuraUrl = "";

    public static EthManager getInstance() {
        return ourInstance;
    }

    public EthManager() {
    }

    /**
     * Initialize EthManager
     *
     * @param mainnetInfuraUrl : Infura Url
     */
    public void init(String mainnetInfuraUrl) {
        this.mainnetInfuraUrl = mainnetInfuraUrl;
        web3j = Web3jFactory.build(new HttpService(mainnetInfuraUrl, false));
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://34.231.96.72:8081")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        hyperLedgerApi = retrofit.create(HyperLedgerApi.class);
    }

    /**
     * Get Current Gas Price
     */
    public BigInteger getGasPrice() {
        try {
            EthGasPrice price = web3j.ethGasPrice()
                    .send();
            return price.getGasPrice();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new BigInteger(Const.DEFAULT_GAS_PRICE);
    }


    /**
     * Create Wallet by password
     */
    public Single<Wallet> createWallet(String password, Context context) {
        return Single.fromCallable(() -> {
            try {
                HashMap<String, Object> body = new HashMap<>();
                body.put("action_type", "WALLET_CREATE");
                body.put("message", "Test");
                sendEventToLedger(body);
                String walletAddress = CenterPrimeUtils.generateNewWalletFile(password, new File(context.getFilesDir(), ""), false);
                String walletPath = context.getFilesDir() + "/" + walletAddress.toLowerCase();
                File keystoreFile = new File(walletPath);
                String keystore = read_file(context, keystoreFile.getName());
                return new Wallet(walletAddress, keystore);
            } catch (CipherException | IOException | NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchProviderException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Get Keystore by wallet address
     */
    public Single<String> getKeyStore(String walletAddress, Context context) {
        return Single.fromCallable(() -> {
            String walletPath = context.getFilesDir() + "/" + walletAddress.toLowerCase();
            File keystoreFile = new File(walletPath);
            if (keystoreFile.exists()) {
                return read_file(context, keystoreFile.getName());
            } else {
                return null;
            }
        });
    }

    /**
     * Import Wallet by Keystore
     */
    public Single<String> importFromKeystore(String keystore, String password, Context context) {
        return Single.fromCallable(() -> {
            try {
                Credentials credentials = CenterPrimeUtils.loadCredentials(password, keystore);
                String walletAddress = CenterPrimeUtils.generateWalletFile(password, credentials.getEcKeyPair(), new File(context.getFilesDir(), ""), false);

                HashMap<String, Object> body = new HashMap<>();
                body.put("action_type", "WALLET_IMPORT_KEYSTORE");
                body.put("message", "TEST");
                sendEventToLedger(body);

                return walletAddress;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Import Wallet with Private Key
     */
    public Single<String> importFromPrivateKey(String privateKey, Context context) {
        return Single.fromCallable(() -> {
            String password = "";
            // Decode private key
            ECKeyPair keys = ECKeyPair.create(Hex.decode(privateKey));
            try {
                Credentials credentials = Credentials.create(keys);
                String walletAddress = CenterPrimeUtils.generateWalletFile(password, credentials.getEcKeyPair(), new File(context.getFilesDir(), ""), false);

                HashMap<String, Object> body = new HashMap<>();
                body.put("action_type", "WALLET_IMPORT_PRIVATE_KEY");
                body.put("message", "TEST");
                sendEventToLedger(body);

                return walletAddress;
            } catch (CipherException | IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Export Private Key
     */
    public Single<String> exportPrivateKey(String walletAddress, String password, Context context) {
        return loadCredentials(walletAddress, password, context)
                .flatMap(credentials -> {
                    String privateKey = credentials.getEcKeyPair().getPrivateKey().toString(16);
                    return Single.just(privateKey);
                });
    }

    /**
     * Get Eth Balance of Wallet
     */
    public Single<BigDecimal> balanceInEth(String address) {
        return Single.fromCallable(() -> {
            BigInteger valueInWei = web3j
                    .ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();
            return BalanceUtils.weiToEth(valueInWei);
        });
    }


    /**
     * Load Credentials
     */
    public Single<Credentials> loadCredentials(String walletAddress, String password, Context context) {
        return getKeyStore(walletAddress, context)
                .flatMap(keystore -> {
                    try {
                        Credentials credentials = CenterPrimeUtils.loadCredentials(password, keystore);
                        return Single.just(credentials);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return Single.error(e);
                    } catch (CipherException e) {
                        e.printStackTrace();
                        return Single.error(e);
                    }
                });
    }

    /**
     * Get ERC20 Token Balance of Wallet
     */
    public Single<BigDecimal> getTokenBalance(String walletAddress, String password, String tokenContractAddress, Context context) {
        return loadCredentials(walletAddress, password, context)
                .flatMap(credentials -> {
                    TransactionReceiptProcessor transactionReceiptProcessor = new NoOpProcessor(web3j);
                    TransactionManager transactionManager = new RawTransactionManager(
                            web3j, credentials, ChainId.MAINNET, transactionReceiptProcessor);
                    Erc20TokenWrapper contract = Erc20TokenWrapper.load(tokenContractAddress, web3j,
                            transactionManager, BigInteger.ZERO, BigInteger.ZERO);
                    Address address = new Address(walletAddress);
                    Uint256 tokenBalance = contract.balanceOf(address);

                    HashMap<String, Object> body = new HashMap<>();
                    body.put("action_type", "GET_TOKEN_BALANCE");
                    body.put("message", "TEST");
                    sendEventToLedger(body);

                    return Single.just(BalanceUtils.weiToEth(tokenBalance.getValue()));
                });
    }

    /**
     * Send Ether
     */
    public Single<String> sendEther(String walletAddress, String password,
                                    BigInteger gasPrice,
                                    BigInteger gasLimit,
                                    BigDecimal etherAmount,
                                    String to_Address,
                                    Context context) {
        return loadCredentials(walletAddress, password, context)
                .flatMap(credentials -> {

                    BigInteger nonce = getNonce(walletAddress);
                    BigDecimal weiValue = Convert.toWei(etherAmount, Convert.Unit.ETHER);

                    RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                            nonce, gasPrice, gasLimit, to_Address, weiValue.toBigIntegerExact());
                    byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
                    String hexValue = Numeric.toHexString(signedMessage);

                    EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).sendAsync().get();

                    String transactionHash = ethSendTransaction.getTransactionHash();

                    HashMap<String, Object> body = new HashMap<>();
                    body.put("action_type", "SEND_ETHER");
                    body.put("message", "TEST");
                    sendEventToLedger(body);


                    return Single.just(transactionHash);
                });
    }

    /**
     * Send Token
     */
    public Single<TransactionReceipt> sendToken(String walletAddress, String password,
                                                BigInteger gasPrice,
                                                BigInteger gasLimit,
                                                BigDecimal tokenAmount,
                                                String to_Address,
                                                String tokenContractAddress,
                                                Context context) {
        return loadCredentials(walletAddress, password, context)
                .flatMap(credentials -> {
                    BigDecimal formattedAmount = BalanceUtils.ethToWei(tokenAmount);
                    TransactionReceiptProcessor transactionReceiptProcessor = new NoOpProcessor(web3j);
                    TransactionManager transactionManager = new RawTransactionManager(
                            web3j, credentials, ChainId.MAINNET, transactionReceiptProcessor);
                    Erc20TokenWrapper contract = Erc20TokenWrapper.load(tokenContractAddress, web3j, transactionManager, gasPrice, gasLimit);
                    TransactionReceipt mReceipt = contract.transfer(new Address(to_Address), new Uint256(formattedAmount.toBigInteger()));

                    HashMap<String, Object> body = new HashMap<>();
                    body.put("action_type", "SEND_TOKEN");
                    body.put("message", "TEST");
                    sendEventToLedger(body);


                    return Single.just(mReceipt);
                });
    }


    /**
     * Get Nonce for Current Wallet Address
     */
    protected BigInteger getNonce(String walletAddress) throws IOException {
        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                walletAddress, DefaultBlockParameterName.PENDING).send();

        return ethGetTransactionCount.getTransactionCount();
    }

    public String read_file(Context context, String filename) throws IOException {
        FileInputStream fis = context.openFileInput(filename);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        BufferedReader bufferedReader = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private void sendEventToLedger(HashMap<String, Object> map) {
        try {
            SubmitTransactionModel submitTransactionModel = new SubmitTransactionModel();
            submitTransactionModel.setTx_type("ETHEREUM");
            submitTransactionModel.setUsername("user1");
            submitTransactionModel.setOrgname("org1");
            submitTransactionModel.setBody(map);
            hyperLedgerApi.submitTransaction(submitTransactionModel)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((objectBaseResponse, throwable) -> {
                        System.out.println(objectBaseResponse);
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
