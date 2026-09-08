package inno.paymentservice.entity;

public enum PaymentCurrency {
    USD("usd"),
    EUR("eur");

    private final String stripeCode;

    PaymentCurrency(String stripeCode) {
        this.stripeCode = stripeCode;
    }

    public String getStripeCode() {
        return stripeCode;
    }
}