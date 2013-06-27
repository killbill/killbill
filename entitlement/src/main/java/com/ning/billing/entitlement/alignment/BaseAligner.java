package com.ning.billing.entitlement.alignment;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Duration;

public class BaseAligner {

    protected DateTime addDuration(final DateTime input, final Duration duration) {
        return addOrRemoveDuration(input, duration, true);
    }

    protected DateTime removeDuration(final DateTime input, final Duration duration) {
        return addOrRemoveDuration(input, duration, false);
    }

    private DateTime addOrRemoveDuration(final DateTime input, final Duration duration, boolean add) {
        DateTime result = input;
        switch (duration.getUnit()) {
            case DAYS:
                result = add ? result.plusDays(duration.getNumber()) : result.minusDays(duration.getNumber());
                ;
                break;

            case MONTHS:
                result = add ? result.plusMonths(duration.getNumber()) : result.minusMonths(duration.getNumber());
                break;

            case YEARS:
                result = add ? result.plusYears(duration.getNumber()) : result.minusYears(duration.getNumber());
                break;
            case UNLIMITED:
            default:
                throw new RuntimeException("Trying to move to unlimited time period");
        }
        return result;
    }
}
