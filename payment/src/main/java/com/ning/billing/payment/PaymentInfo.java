package com.ning.billing.payment;

import java.util.UUID;

import com.ning.billing.util.eventbus.IEventBusType;

public class PaymentInfo implements IEventBusType {
    private final UUID id;

    public PaymentInfo(PaymentInfo src) {
        this.id = src.id;
    }

    public PaymentInfo(UUID id) {
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
        PaymentInfo other = (PaymentInfo) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        }
        else if (!id.equals(other.id))
            return false;
        return true;
    }
}
