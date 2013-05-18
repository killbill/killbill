package com.ning.billing.payment.api;

public class PaymentMethodKVInfo {

    private final String key;
    private final Object value;
    private final Boolean isUpdatable;

    public PaymentMethodKVInfo(final String key, final Object value, final Boolean isUpdatable) {
        this.key = key;
        this.value = value;
        this.isUpdatable = isUpdatable;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public Boolean getIsUpdatable() {
        return isUpdatable;
    }



    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PaymentMethodKVInfo");
        sb.append("{key='").append(key).append('\'');
        sb.append(", value=").append(value);
        sb.append(", isUpdatable=").append(isUpdatable);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final PaymentMethodKVInfo that = (PaymentMethodKVInfo) o;

        if (isUpdatable != null ? !isUpdatable.equals(that.isUpdatable) : that.isUpdatable != null) {
            return false;
        }
        if (key != null ? !key.equals(that.key) : that.key != null) {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (isUpdatable != null ? isUpdatable.hashCode() : 0);
        return result;
    }
}
