package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct;

public interface MerchPaymentGateway {
    default String currency() { return "gbp"; }

    boolean isConfigured();

    boolean isSimulationMode();

    String providerName();

    MerchHostedCheckoutSession createCheckoutSession(MerchOrder order,
                                                     MerchProduct product,
                                                     int quantity,
                                                     String successUrl,
                                                     String cancelUrl);

    boolean expireCheckoutSession(String paymentReference);

    MerchPaymentVerification verifyCheckoutSession(String paymentReference);
}
