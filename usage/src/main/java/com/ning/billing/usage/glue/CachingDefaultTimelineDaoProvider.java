package com.ning.billing.usage.glue;

import javax.inject.Provider;

import org.skife.jdbi.v2.DBI;

import com.ning.billing.usage.timeline.persistent.CachingTimelineDao;
import com.ning.billing.usage.timeline.persistent.DefaultTimelineDao;
import com.ning.billing.usage.timeline.persistent.TimelineDao;

import com.google.inject.Inject;

public class CachingDefaultTimelineDaoProvider implements Provider<TimelineDao> {

    private final DBI dbi;

    @Inject
    public CachingDefaultTimelineDaoProvider(final DBI dbi) {
        this.dbi = dbi;
    }

    @Override
    public TimelineDao get() {
        final TimelineDao delegate = new DefaultTimelineDao(dbi);

        return new CachingTimelineDao(delegate);
    }
}
