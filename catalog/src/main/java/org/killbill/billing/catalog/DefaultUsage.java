package org.killbill.billing.catalog;

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.util.config.catalog.ValidatingConfig;
import org.killbill.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultUsage extends ValidatingConfig<StandaloneCatalog> implements Usage {

    @XmlElement(required = true)
    private BillingPeriod billingPeriod;

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    // Not exposed in xml.
    private PlanPhase phase;

    @Override
    public boolean compliesWithLimits(final String s, final double v) {
        return false;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog root, final ValidationErrors errors) {
        return null;
    }

    @Override
    public void initialize(final StandaloneCatalog root, final URI uri) {

    }

    public DefaultUsage setBillingPeriod(final BillingPeriod billingPeriod) {
        this.billingPeriod = billingPeriod;
        return this;
    }

    public DefaultUsage setPhase(final PlanPhase phase) {
        this.phase = phase;
        return this;
    }

    /*
        /*
     * (non-Javadoc)
     *
     * @see org.killbill.billing.catalog.PlanPhase#getLimit()
     */


    /*
    @Override
    public DefaultLimit[] getLimits() {
        return limits;
    }


    protected Limit findLimit(String unit) {

        for (Limit limit : limits) {
            if (limit.getUnit().getName().equals(unit)) {
                return limit;
            }
        }
        return null;
    }

    @Override
    public boolean compliesWithLimits(String unit, double value) {
        Limit l = findLimit(unit);
        if (l == null) {
            return getPlan().getProduct().compliesWithLimits(unit, value);
        }
        return l.compliesWith(value);
    }


    */

    /*
    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = true)
    private DefaultLimit[] limits = new DefaultLimit[0];
    */

}
