/*
 * Copyright 2024 PayTheFly
 * Copyright 2024 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.plugin.paythefly;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Pure-Java EIP-712 structured-data signer for the PayTheFlyPro domain.
 *
 * <p>Implements EIP-712 hashing manually using web3j primitives, producing
 * the same signature that the PayTheFly smart contract verifies on-chain.</p>
 *
 * <h3>Domain Separator</h3>
 * <pre>
 * EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)
 *   name = "PayTheFlyPro"
 *   version = "1"
 * </pre>
 *
 * <h3>PaymentRequest type</h3>
 * <pre>
 * PaymentRequest(string projectId,address token,uint256 amount,string serialNo,uint256 deadline)
 * </pre>
 *
 * <h3>WithdrawalRequest type</h3>
 * <pre>
 * WithdrawalRequest(address user,string projectId,address token,uint256 amount,string serialNo,uint256 deadline)
 * </pre>
 */
public class EIP712Signer {

    private static final String DOMAIN_NAME = "PayTheFlyPro";
    private static final String DOMAIN_VERSION = "1";

    // Pre-compute type hashes
    private static final byte[] EIP712_DOMAIN_TYPEHASH = Hash.sha3(
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".getBytes(StandardCharsets.UTF_8)
    );
    private static final byte[] PAYMENT_REQUEST_TYPEHASH = Hash.sha3(
            "PaymentRequest(string projectId,address token,uint256 amount,string serialNo,uint256 deadline)".getBytes(StandardCharsets.UTF_8)
    );
    private static final byte[] WITHDRAWAL_REQUEST_TYPEHASH = Hash.sha3(
            "WithdrawalRequest(address user,string projectId,address token,uint256 amount,string serialNo,uint256 deadline)".getBytes(StandardCharsets.UTF_8)
    );

    private final ECKeyPair keyPair;
    private final long chainId;
    private final String verifyingContract;
    private final byte[] domainSeparator;

    /**
     * Create a new signer.
     *
     * @param privateKeyHex      hex-encoded private key (with or without {@code 0x} prefix)
     * @param chainId            EIP-155 chain id (56 for BSC, 728126428 for TRON)
     * @param verifyingContract  smart-contract address that will verify the signature
     */
    public EIP712Signer(final String privateKeyHex, final long chainId, final String verifyingContract) {
        this.keyPair = Credentials.create(privateKeyHex).getEcKeyPair();
        this.chainId = chainId;
        this.verifyingContract = verifyingContract;
        this.domainSeparator = computeDomainSeparator();
    }

    // ----- Public API -----

    /**
     * Sign a PaymentRequest and return the {@code 0x}-prefixed 65-byte signature.
     *
     * @param projectId  PayTheFly project id
     * @param token      token contract address
     * @param amountWei  amount in raw units (wei for BSC, sun for TRON)
     * @param serialNo   unique serial number (KB transaction id)
     * @param deadline   unix-timestamp deadline
     * @return hex-encoded signature with {@code 0x} prefix
     */
    public String signPaymentRequest(final String projectId,
                                     final String token,
                                     final BigInteger amountWei,
                                     final String serialNo,
                                     final long deadline) {
        final byte[] structHash = hashPaymentRequest(projectId, token, amountWei, serialNo, deadline);
        return sign(structHash);
    }

    /**
     * Sign a WithdrawalRequest and return the {@code 0x}-prefixed 65-byte signature.
     *
     * @param user       user wallet address
     * @param projectId  PayTheFly project id
     * @param token      token contract address
     * @param amountWei  amount in raw units
     * @param serialNo   unique serial number
     * @param deadline   unix-timestamp deadline
     * @return hex-encoded signature with {@code 0x} prefix
     */
    public String signWithdrawalRequest(final String user,
                                        final String projectId,
                                        final String token,
                                        final BigInteger amountWei,
                                        final String serialNo,
                                        final long deadline) {
        final byte[] structHash = hashWithdrawalRequest(user, projectId, token, amountWei, serialNo, deadline);
        return sign(structHash);
    }

    // ----- Internals -----

    private byte[] computeDomainSeparator() {
        // encode: typehash ++ keccak(name) ++ keccak(version) ++ uint256(chainId) ++ address(verifyingContract)
        final byte[] nameHash = Hash.sha3(DOMAIN_NAME.getBytes(StandardCharsets.UTF_8));
        final byte[] versionHash = Hash.sha3(DOMAIN_VERSION.getBytes(StandardCharsets.UTF_8));

        final byte[] encoded = ByteBuffer.allocate(5 * 32)
                .put(padLeft(EIP712_DOMAIN_TYPEHASH, 32))
                .put(padLeft(nameHash, 32))
                .put(padLeft(versionHash, 32))
                .put(padLeft(Numeric.toBytesPadded(BigInteger.valueOf(chainId), 32), 32))
                .put(padLeft(Numeric.hexStringToByteArray(TypeEncoder.encode(new Address(verifyingContract))), 32))
                .array();
        return Hash.sha3(encoded);
    }

    private byte[] hashPaymentRequest(final String projectId,
                                      final String token,
                                      final BigInteger amountWei,
                                      final String serialNo,
                                      final long deadline) {
        final byte[] projectIdHash = Hash.sha3(projectId.getBytes(StandardCharsets.UTF_8));
        final byte[] serialNoHash = Hash.sha3(serialNo.getBytes(StandardCharsets.UTF_8));

        final byte[] encoded = ByteBuffer.allocate(6 * 32)
                .put(padLeft(PAYMENT_REQUEST_TYPEHASH, 32))
                .put(padLeft(projectIdHash, 32))
                .put(padLeft(Numeric.hexStringToByteArray(TypeEncoder.encode(new Address(token))), 32))
                .put(padLeft(Numeric.hexStringToByteArray(TypeEncoder.encode(new Uint256(amountWei))), 32))
                .put(padLeft(serialNoHash, 32))
                .put(padLeft(Numeric.hexStringToByteArray(TypeEncoder.encode(new Uint256(BigInteger.valueOf(deadline)))), 32))
                .array();
        return Hash.sha3(encoded);
    }

    private byte[] hashWithdrawalRequest(final String user,
                                         final String projectId,
                                         final String token,
                                         final BigInteger amountWei,
                                         final String serialNo,
                                         final long deadline) {
        final byte[] projectIdHash = Hash.sha3(projectId.getBytes(StandardCharsets.UTF_8));
        final byte[] serialNoHash = Hash.sha3(serialNo.getBytes(StandardCharsets.UTF_8));

        final byte[] encoded = ByteBuffer.allocate(7 * 32)
                .put(padLeft(WITHDRAWAL_REQUEST_TYPEHASH, 32))
                .put(padLeft(Numeric.hexStringToByteArray(TypeEncoder.encode(new Address(user))), 32))
                .put(padLeft(projectIdHash, 32))
                .put(padLeft(Numeric.hexStringToByteArray(TypeEncoder.encode(new Address(token))), 32))
                .put(padLeft(Numeric.hexStringToByteArray(TypeEncoder.encode(new Uint256(amountWei))), 32))
                .put(padLeft(serialNoHash, 32))
                .put(padLeft(Numeric.hexStringToByteArray(TypeEncoder.encode(new Uint256(BigInteger.valueOf(deadline)))), 32))
                .array();
        return Hash.sha3(encoded);
    }

    /**
     * EIP-712: digest = keccak256("\x19\x01" ++ domainSeparator ++ structHash)
     */
    private String sign(final byte[] structHash) {
        final byte[] digest = Hash.sha3(ByteBuffer.allocate(2 + 32 + 32)
                .put((byte) 0x19)
                .put((byte) 0x01)
                .put(domainSeparator)
                .put(structHash)
                .array());

        final Sign.SignatureData sigData = Sign.signMessage(digest, keyPair, false);

        // Pack r + s + v into 65 bytes
        final byte[] sig = new byte[65];
        System.arraycopy(sigData.getR(), 0, sig, 0, 32);
        System.arraycopy(sigData.getS(), 0, sig, 32, 32);
        sig[64] = sigData.getV()[0];

        return Numeric.toHexString(sig);
    }

    /**
     * Pad (or trim) a byte array to exactly {@code length} bytes, left-aligned.
     */
    private static byte[] padLeft(final byte[] src, final int length) {
        if (src.length == length) {
            return src;
        }
        final byte[] result = new byte[length];
        final int offset = Math.max(0, length - src.length);
        System.arraycopy(src, 0, result, offset, Math.min(src.length, length));
        return result;
    }
}
