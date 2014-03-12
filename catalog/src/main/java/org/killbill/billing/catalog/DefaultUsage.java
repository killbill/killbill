package org.killbill.billing.catalog;

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.util.config.catalog.ValidatingConfig;
import org.killbill.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultUsage extends ValidatingConfig<StandaloneCatalog> implements Usage {

    @XmlElement(required = true)
    private BillingPeriod billingPeriod;

    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = true)
    private DefaultLimit[] limits = new DefaultLimit[0];

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    // Not exposed in xml.
    private PlanPhase phase;

    @Override
    public boolean compliesWithLimits(final String unit, final double value) {
        for (DefaultLimit limit : limits) {
            if (!limit.getUnit().getName().equals(unit)) {
                continue;
            }
            if (!limit.compliesWith(value)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog root, final ValidationErrors errors) {
        validateCollection(root, errors, limits);
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog root, final URI uri) {

    }

    @Override
    public DefaultLimit[] getLimits() {
        return limits;
    }

    public DefaultUsage setBillingPeriod(final BillingPeriod billingPeriod) {
        this.billingPeriod = billingPeriod;
        return this;
    }

    public DefaultUsage setPhase(final PlanPhase phase) {
        this.phase = phase;
        return this;
    }

    public void setLimits(final DefaultLimit[] limits) {
        this.limits = limits;
    }

    protected Limit findLimit(String unit) {

        for (Limit limit : limits) {
            if (limit.getUnit().getName().equals(unit)) {
                return limit;
            }
        }
        return null;
    }
}
