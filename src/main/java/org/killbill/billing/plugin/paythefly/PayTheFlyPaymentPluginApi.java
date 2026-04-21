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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.payment.PluginGatewayNotification;
import org.killbill.billing.plugin.api.payment.PluginHostedPaymentPageFormDescriptor;
import org.killbill.billing.plugin.paythefly.dao.PayTheFlyDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

/**
 * Kill Bill PaymentPluginApi for PayTheFly Web3 crypto payments.
 */
public class PayTheFlyPaymentPluginApi implements PaymentPluginApi {

    private static final Logger logger = LoggerFactory.getLogger(PayTheFlyPaymentPluginApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String PROP_PAY_URL = "payTheFlyPayUrl";
    public static final String PROP_WITHDRAW_URL = "payTheFlyWithdrawUrl";
    public static final String PROP_SIGNATURE = "payTheFlySignature";
    public static final String PROP_DEADLINE = "payTheFlyDeadline";
    public static final String PROP_CHAIN_ID = "payTheFlyChainId";
    public static final String PROP_SERIAL_NO = "payTheFlySerialNo";

    private final PayTheFlyConfigPropertiesConfigurationHandler configHandler;
    private final OSGIKillbillAPI killbillAPI;
    private final Clock clock;
    private final PayTheFlyDao dao;

    public PayTheFlyPaymentPluginApi(final PayTheFlyConfigPropertiesConfigurationHandler configHandler,
                                     final OSGIKillbillAPI killbillAPI,
                                     final OSGIConfigPropertiesService configProperties,
                                     final Clock clock,
                                     final PayTheFlyDao dao) {
        this.configHandler = configHandler;
        this.killbillAPI = killbillAPI;
        this.clock = clock;
        this.dao = dao;
    }

    @Override
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount,
            final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return unsupported(kbPaymentId, kbTransactionId, TransactionType.AUTHORIZE, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount,
            final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return unsupported(kbPaymentId, kbTransactionId, TransactionType.CAPTURE, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount,
            final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        final PayTheFlyConfigProperties config = configHandler.getConfigurable(context.getTenantId());
        validateConfig(config);

        final long chainId = longProp("chainId", config.getChainId(), properties);
        final String tokenAddress = strProp("tokenAddress", config.getTokenAddress(), properties);
        final String serialNo = kbTransactionId.toString();
        final long deadline = nowSecs() + config.getPaymentDeadlineSeconds();
        final int decimals = PayTheFlyConfigProperties.decimalsForChain(chainId);
        final BigInteger amountRaw = amount.movePointRight(decimals).toBigIntegerExact();

        final EIP712Signer signer = new EIP712Signer(config.getSignerPrivateKey(), chainId, config.getVerifyingContract());
        final String signature = signer.signPaymentRequest(config.getProjectId(), tokenAddress, amountRaw, serialNo, deadline);
        final String payUrl = payUrl(config, chainId, tokenAddress, amount.toPlainString(), serialNo, deadline, signature);

        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("serialNo", serialNo);
        data.put("payUrl", payUrl);
        data.put("signature", signature);
        data.put("chainId", chainId);
        data.put("tokenAddress", tokenAddress);
        data.put("deadline", deadline);
        data.put("status", PaymentPluginStatus.PENDING.toString());
        try {
            dao.addResponse(kbAccountId, kbPaymentId, kbTransactionId, TransactionType.PURCHASE.toString(),
                    amount, currency != null ? currency.toString() : null, serialNo, data, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Failed to persist response", e);
        }

        logger.info("PayTheFly purchase: serial={}, amount={}, chain={}", serialNo, amount, chainId);

        return new PayTheFlyPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.PURCHASE,
                amount, currency, PaymentPluginStatus.PENDING, null, serialNo,
                clock.getUTCNow(), clock.getUTCNow(),
                ImmutableList.of(
                        new PluginProperty(PROP_PAY_URL, payUrl, false),
                        new PluginProperty(PROP_SIGNATURE, signature, false),
                        new PluginProperty(PROP_DEADLINE, String.valueOf(deadline), false),
                        new PluginProperty(PROP_CHAIN_ID, String.valueOf(chainId), false),
                        new PluginProperty(PROP_SERIAL_NO, serialNo, false)),
                data);
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return unsupported(kbPaymentId, kbTransactionId, TransactionType.VOID, null, null);
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount,
            final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return unsupported(kbPaymentId, kbTransactionId, TransactionType.CREDIT, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin refundPayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount,
            final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        final PayTheFlyConfigProperties config = configHandler.getConfigurable(context.getTenantId());
        validateConfig(config);

        final String userWallet = strProp("userWallet", null, properties);
        if (userWallet == null || userWallet.isEmpty()) {
            throw new PaymentPluginApiException("VALIDATION", "userWallet property required for refund");
        }

        final long chainId = longProp("chainId", config.getChainId(), properties);
        final String tokenAddress = strProp("tokenAddress", config.getTokenAddress(), properties);
        final String serialNo = kbTransactionId.toString();
        final long deadline = nowSecs() + config.getPaymentDeadlineSeconds();
        final int decimals = PayTheFlyConfigProperties.decimalsForChain(chainId);
        final BigInteger amountRaw = amount.movePointRight(decimals).toBigIntegerExact();

        final EIP712Signer signer = new EIP712Signer(config.getSignerPrivateKey(), chainId, config.getVerifyingContract());
        final String signature = signer.signWithdrawalRequest(userWallet, config.getProjectId(), tokenAddress, amountRaw, serialNo, deadline);
        final String withdrawUrl = withdrawUrl(config, chainId, tokenAddress, userWallet, amount.toPlainString(), serialNo, deadline, signature);

        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("serialNo", serialNo);
        data.put("withdrawUrl", withdrawUrl);
        data.put("signature", signature);
        data.put("chainId", chainId);
        data.put("tokenAddress", tokenAddress);
        data.put("userWallet", userWallet);
        data.put("deadline", deadline);
        data.put("status", PaymentPluginStatus.PENDING.toString());

        try {
            dao.addResponse(kbAccountId, kbPaymentId, kbTransactionId, TransactionType.REFUND.toString(),
                    amount, currency != null ? currency.toString() : null, serialNo, data, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Failed to persist refund response", e);
        }

        logger.info("PayTheFly refund: serial={}, amount={}, chain={}, user={}", serialNo, amount, chainId, userWallet);

        return new PayTheFlyPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.REFUND,
                amount, currency, PaymentPluginStatus.PENDING, null, serialNo,
                clock.getUTCNow(), clock.getUTCNow(),
                ImmutableList.of(
                        new PluginProperty(PROP_WITHDRAW_URL, withdrawUrl, false),
                        new PluginProperty(PROP_SIGNATURE, signature, false),
                        new PluginProperty(PROP_DEADLINE, String.valueOf(deadline), false),
                        new PluginProperty(PROP_CHAIN_ID, String.valueOf(chainId), false),
                        new PluginProperty(PROP_SERIAL_NO, serialNo, false)),
                data);
    }

    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId,
            final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        try {
            final List<Map<String, Object>> rows = dao.getResponses(kbPaymentId, context.getTenantId());
            final List<PaymentTransactionInfoPlugin> result = new ArrayList<>();
            for (final Map<String, Object> row : rows) {
                result.add(PayTheFlyPaymentTransactionInfoPlugin.fromRow(row));
            }
            return result;
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Failed to get payment info", e);
        }
    }

    @Override
    public Pagination<PaymentTransactionInfoPlugin> searchPayments(final String searchKey, final Long offset,
            final Long limit, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentPluginApiException {
        return new EmptyPagination<>();
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId,
            final PaymentMethodPlugin paymentMethodProps, final boolean setDefault,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        final Iterable<PluginProperty> all = PluginProperties.merge(paymentMethodProps.getProperties(), properties);
        final String wallet = strProp("walletAddress", strProp("token", "unspecified", all), all);
        final Map<String, Object> addl = new LinkedHashMap<>();
        addl.put("walletAddress", wallet);
        addl.put("chainId", strProp("chainId", "", all));
        try {
            dao.addPaymentMethod(kbAccountId, kbPaymentMethodId, wallet, setDefault, addl, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Failed to add payment method", e);
        }
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        try {
            dao.deletePaymentMethod(kbPaymentMethodId, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Failed to delete payment method", e);
        }
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId,
            final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        try {
            final Map<String, Object> row = dao.getPaymentMethod(kbPaymentMethodId, context.getTenantId());
            if (row == null) {
                return new PayTheFlyPaymentMethodPlugin(kbPaymentMethodId, null, false, ImmutableList.of());
            }
            final String w = (String) row.get("wallet_address");
            return new PayTheFlyPaymentMethodPlugin(kbPaymentMethodId, w, false,
                    ImmutableList.of(new PluginProperty("walletAddress", w, false)));
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Failed to get payment method detail", e);
        }
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        // no-op
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        try {
            final List<Map<String, Object>> rows = dao.getPaymentMethods(kbAccountId, context.getTenantId());
            final List<PaymentMethodInfoPlugin> result = new ArrayList<>();
            for (final Map<String, Object> row : rows) {
                result.add(new PayTheFlyPaymentMethodInfoPlugin(row));
            }
            return result;
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Failed to get payment methods", e);
        }
    }

    @Override
    public Pagination<PaymentMethodPlugin> searchPaymentMethods(final String searchKey, final Long offset,
            final Long limit, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentPluginApiException {
        return new EmptyPagination<>();
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> paymentMethods,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        // no-op
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId,
            final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties,
            final CallContext context) throws PaymentPluginApiException {
        final PayTheFlyConfigProperties config = configHandler.getConfigurable(context.getTenantId());
        validateConfig(config);

        final String amount = strProp("amount", "0", customFields);
        final long chainId = longProp("chainId", config.getChainId(), customFields);
        final String tokenAddress = strProp("tokenAddress", config.getTokenAddress(), customFields);
        final String serialNo = strProp("serialNo", UUID.randomUUID().toString(), customFields);
        final long deadline = nowSecs() + config.getPaymentDeadlineSeconds();
        final int decimals = PayTheFlyConfigProperties.decimalsForChain(chainId);
        final BigInteger amountRaw = new BigDecimal(amount).movePointRight(decimals).toBigIntegerExact();

        final EIP712Signer signer = new EIP712Signer(config.getSignerPrivateKey(), chainId, config.getVerifyingContract());
        final String signature = signer.signPaymentRequest(config.getProjectId(), tokenAddress, amountRaw, serialNo, deadline);
        final String url = payUrl(config, chainId, tokenAddress, amount, serialNo, deadline, signature);

        final Map<String, Object> hppData = new LinkedHashMap<>();
        hppData.put("payUrl", url);
        hppData.put("serialNo", serialNo);
        hppData.put("signature", signature);
        try {
            dao.addHppRequest(kbAccountId, null, null, serialNo, hppData, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Failed to persist HPP request", e);
        }

        return new PluginHostedPaymentPageFormDescriptor(kbAccountId, url, ImmutableList.of(
                new PluginProperty(PROP_PAY_URL, url, false),
                new PluginProperty(PROP_SIGNATURE, signature, false),
                new PluginProperty(PROP_SERIAL_NO, serialNo, false)));
    }

    @Override
    public GatewayNotification processNotification(final String notification,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        logger.info("PayTheFly webhook received: {}", notification);
        final PayTheFlyConfigProperties config = configHandler.getConfigurable(context.getTenantId());

        try {
            final JsonNode root = MAPPER.readTree(notification);
            final String dataStr = root.get("data").asText();
            final String sign = root.get("sign").asText();
            final long timestamp = root.get("timestamp").asLong();

            if (!PayTheFlyWebhookVerifier.verify(dataStr, timestamp, sign, config.getProjectKey())) {
                logger.error("PayTheFly webhook: HMAC verification failed");
                throw new PaymentPluginApiException("HMAC_FAILED", "Webhook signature verification failed");
            }

            final JsonNode data = MAPPER.readTree(dataStr);
            final String serialNo = data.get("serial_no").asText();
            final String txHash = data.get("tx_hash").asText();
            final boolean confirmed = data.get("confirmed").asBoolean();
            final int txType = data.get("tx_type").asInt();
            final String wallet = data.get("wallet").asText();
            final String value = data.get("value").asText();
            final String fee = data.has("fee") ? data.get("fee").asText() : "0";

            final Map<String, Object> existingRow = dao.getResponseBySerialNo(serialNo, context.getTenantId());
            if (existingRow != null) {
                final UUID kbTxId = UUID.fromString((String) existingRow.get("kb_payment_transaction_id"));
                final Map<String, Object> updated = new LinkedHashMap<>();
                final String existing = (String) existingRow.get("additional_data");
                if (existing != null) {
                    updated.putAll(PayTheFlyDao.fromJson(existing));
                }
                updated.put("txHash", txHash);
                updated.put("confirmed", confirmed);
                updated.put("txType", txType);
                updated.put("wallet", wallet);
                updated.put("webhookValue", value);
                updated.put("webhookFee", fee);
                updated.put("status", confirmed ? PaymentPluginStatus.PROCESSED.toString() : PaymentPluginStatus.PENDING.toString());
                dao.updateResponseStatus(kbTxId, updated, context.getTenantId());
                logger.info("PayTheFly webhook OK: serial={}, tx={}, confirmed={}", serialNo, txHash, confirmed);
            } else {
                logger.warn("PayTheFly webhook: no match for serial_no={}", serialNo);
            }

            // Response MUST contain "success" string per PayTheFly API spec
            return new PluginGatewayNotification(UUID.randomUUID(), 200, "{\"result\":\"success\"}", ImmutableList.of());

        } catch (final PaymentPluginApiException e) {
            throw e;
        } catch (final Exception e) {
            logger.error("PayTheFly webhook error", e);
            throw new PaymentPluginApiException("WEBHOOK_ERROR", e.getMessage());
        }
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    static String payUrl(final PayTheFlyConfigProperties c, final long chainId, final String token,
                         final String amount, final String serialNo, final long deadline, final String sig) {
        return c.getBaseUrl() + "/pay?chainId=" + chainId
                + "&projectId=" + enc(c.getProjectId())
                + "&amount=" + enc(amount)
                + "&serialNo=" + enc(serialNo)
                + "&deadline=" + deadline
                + "&signature=" + enc(sig)
                + "&token=" + enc(token);
    }

    static String withdrawUrl(final PayTheFlyConfigProperties c, final long chainId, final String token,
                              final String user, final String amount, final String serialNo,
                              final long deadline, final String sig) {
        return c.getBaseUrl() + "/withdraw?chainId=" + chainId
                + "&projectId=" + enc(c.getProjectId())
                + "&amount=" + enc(amount)
                + "&serialNo=" + enc(serialNo)
                + "&deadline=" + deadline
                + "&signature=" + enc(sig)
                + "&token=" + enc(token)
                + "&user=" + enc(user);
    }

    private static String enc(final String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private long nowSecs() {
        return clock.getUTCNow().getMillis() / 1000;
    }

    private static void validateConfig(final PayTheFlyConfigProperties config) throws PaymentPluginApiException {
        if (!config.isConfigured()) {
            throw new PaymentPluginApiException("CONFIG_MISSING",
                    "PayTheFly plugin is not configured (projectId, projectKey, signerPrivateKey, verifyingContract required)");
        }
    }

    private static String strProp(final String key, final String defaultValue, final Iterable<PluginProperty> props) {
        final String val = PluginProperties.findPluginPropertyValue(key, props);
        return val != null ? val : defaultValue;
    }

    private static long longProp(final String key, final long defaultValue, final Iterable<PluginProperty> props) {
        final String val = PluginProperties.findPluginPropertyValue(key, props);
        return val != null ? Long.parseLong(val) : defaultValue;
    }

    private PaymentTransactionInfoPlugin unsupported(final UUID kbPaymentId, final UUID kbTransactionId,
            final TransactionType type, final BigDecimal amount, final Currency currency) {
        return new PayTheFlyPaymentTransactionInfoPlugin(kbPaymentId, kbTransactionId, type,
                amount, currency, PaymentPluginStatus.CANCELED, "Unsupported operation for Web3 payments",
                null, clock.getUTCNow(), clock.getUTCNow(), ImmutableList.of(), null);
    }
}
