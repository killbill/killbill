package com.ning.billing.payment;

import java.util.UUID;

import com.ning.billing.util.eventbus.IEventBusType;

public class PaymentError implements IEventBusType {
    private final UUID id;

    public PaymentError(PaymentError src) {
        this.id = src.id;
    }

    public PaymentError(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PaymentError other = (PaymentError) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        }
        else if (!id.equals(other.id))
            return false;
        return true;
    }
}
