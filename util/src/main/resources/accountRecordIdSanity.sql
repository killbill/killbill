select
  'AUDIT_LOG' as table_name
, sum(count) count
from (
  select
   'ACCOUNT_EMAIL_HISTORY' table_name
  , count(1) count
  from audit_log al
  join account_email_history t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'ACCOUNT_EMAIL_HISTORY'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'BLOCKING_STATES' table_name
  , count(1) count
  from audit_log al
  join blocking_states t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'BLOCKING_STATES'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'BUNDLES' table_name
  , count(1) count
  from audit_log al
  join bundles t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'BUNDLES'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'CUSTOM_FIELD_HISTORY' table_name
  , count(1) count
  from audit_log al
  join custom_field_history t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'CUSTOM_FIELD_HISTORY'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'INVOICES' table_name
  , count(1) count
  from audit_log al
  join invoices t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'INVOICES'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'INVOICE_ITEMS' table_name
  , count(1) count
  from audit_log al
  join invoice_items t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'INVOICE_ITEMS'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'INVOICE_PAYMENTS' table_name
  , count(1) count
  from audit_log al
  join invoice_payments t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'INVOICE_PAYMENTS'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'PAYMENTS' table_name
  , count(1) count
  from audit_log al
  join payments t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'PAYMENTS'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'PAYMENT_ATTEMPTS' table_name
  , count(1) count
  from audit_log al
  join payment_attempts t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'PAYMENT_ATTEMPTS'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'PAYMENT_ATTEMPT_HISTORY' table_name
  , count(1) count
  from audit_log al
  join payment_attempt_history t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'PAYMENT_ATTEMPT_HISTORY'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'PAYMENT_HISTORY' table_name
  , count(1) count
  from audit_log al
  join payment_history t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'PAYMENT_HISTORY'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'PAYMENT_METHODS' table_name
  , count(1) count
  from audit_log al
  join payment_methods t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'PAYMENT_METHODS'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'PAYMENT_METHOD_HISTORY' table_name
  , count(1) count
  from audit_log al
  join payment_method_history t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'PAYMENT_METHOD_HISTORY'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'REFUNDS' table_name
  , count(1) count
  from audit_log al
  join refunds t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'REFUNDS'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'REFUND_HISTORY' table_name
  , count(1) count
  from audit_log al
  join refund_history t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'REFUND_HISTORY'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'SUBSCRIPTIONS' table_name
  , count(1) count
  from audit_log al
  join subscriptions t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'SUBSCRIPTIONS'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'SUBSCRIPTION_EVENTS' table_name
  , count(1) count
  from audit_log al
  join subscription_events t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'SUBSCRIPTION_EVENTS'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
  union
  select
    'TAG_HISTORY' table_name
  , count(1) count
  from audit_log al
  join tag_history t on al.target_record_id = t.record_id
  where 1 = 1
  and al.table_name = 'TAG_HISTORY'
  and (
       al.account_record_id != t.account_record_id
    or al.account_record_id is null
  )
) audit
union
select
  'ACCOUNT_EMAILS' as table_name
, count(1) count
from account_emails ae
left outer join accounts a on ae.account_id = a.id
where 1 = 1
and (
     ae.account_record_id != a.record_id
  or ae.account_record_id is null
)
union
select
  'ACCOUNT_EMAIL_HISTORY' as table_name
, count(1) count
from account_email_history aeh
left outer join accounts a on aeh.account_id = a.id
where 1 = 1
and (
     aeh.account_record_id != a.record_id
  or aeh.account_record_id is null
)
union
select
  'ACCOUNT_HISTORY' as table_name
, count(1) count
from account_history ah
left outer join accounts a on ah.id = a.id
where 1 = 1
and (
     ah.target_record_id != a.record_id
  or ah.target_record_id is null
)
union
select
  'BLOCKING_STATES' as table_name
, count(1) count
from blocking_states bs
left outer join bundles b on bs.blockable_id = b.id and bs.type = 'SUBSCRIPTION_BUNDLE'
left outer join accounts a on b.account_id = a.id
where 1 = 1
and (
     bs.account_record_id != a.record_id
  or bs.account_record_id is null
)
union
select
  'BUNDLES' as table_name
, count(1) count
from bundles b
left outer join accounts a on b.account_id = a.id
where 1 = 1
and (
     b.account_record_id != a.record_id
  or b.account_record_id is null
)
union
select
  'BUS_EVENTS' as table_name
, ifnull(sum(count), 0) count
from (
  select
    class_name
  , count(1) count
  from (
    select
      substr(event_json, position('accountId' in event_json) + 12, 36) id
    , search_key1  account_record_id
    , class_name
    from bus_events
    where 1 = 1
    and class_name in ('org.killbill.billing.account.api.user.DefaultAccountChangeEvent', 'org.killbill.billing.invoice.api.user.DefaultNullInvoiceEvent', 'org.killbill.billing.payment.api.DefaultPaymentInfoEvent', 'org.killbill.billing.invoice.api.user.DefaultInvoiceAdjustmentEvent', 'org.killbill.billing.payment.api.DefaultPaymentErrorEvent')
    union all
    select
      substr(event_json,position('objectId' in event_json) + 11, 36) id
    , search_key1 account_record_id
    , class_name
    from bus_events
    where 1 = 1
    and class_name in ('org.killbill.billing.util.tag.api.user.DefaultUserTagCreationEvent', 'org.killbill.billing.util.tag.api.user.DefaultControlTagCreationEvent', 'org.killbill.billing.util.tag.api.user.DefaultControlTagDeletionEvent', 'org.killbill.billing.util.tag.api.user.DefaultUserTagDeletionEvent')
  ) be
  left outer join accounts a using (id)
  where 1 = 1
  and (
       be.account_record_id is null
    or be.account_record_id != a.record_id
  )
  group by class_name
  union all
  select
    class_name
  , count(1) count
  from (
    select
      substr(event_json, position('subscriptionId' in event_json) + 17, 36) id
    , search_key1 account_record_id
    , class_name
    from bus_events
    where 1 = 1
    and class_name in ('org.killbill.billing.entitlement.api.user.DefaultRequestedSubscriptionEvent', 'org.killbill.billing.entitlement.api.user.DefaultEffectiveSubscriptionEvent')
  ) be
  left outer join subscriptions s using (id)
  where 1 = 1
  and (
       be.account_record_id is null
    or be.account_record_id != s.account_record_id
  )
  group by class_name
  union all
  select
    class_name
  , count(1) count
  from (
    select
      substr(event_json, position('invoiceId' in event_json) + 12, 36) id
    , search_key1 account_record_id
    , class_name
    from bus_events
    where 1 = 1
    and class_name in ('org.killbill.billing.invoice.api.user.DefaultInvoiceCreationEvent')
  ) be
  left outer join invoices i using (id)
  where 1 = 1
  and (
       be.account_record_id is null
    or be.account_record_id != i.account_record_id
  )
  group by class_name
  union all
  select
    class_name
  , count(1) count
  from (
    select
      substr(event_json, position('overdueObjectId' in event_json) + 18, 36) id
    , search_key1 account_record_id
    , class_name
    from bus_events
    where 1 = 1
    and class_name in ('org.killbill.billing.overdue.applicator.DefaultOverdueChangeEvent')
  ) be
  left outer join bundles b using (id)
  where 1 = 1
  and (
       be.account_record_id is null
    or be.account_record_id != b.account_record_id
  )
  group by class_name
) bus
union
select
  'CUSTOM_FIELD_HISTORY' as table_name
, count(1) count
from custom_field_history cfh
left outer join accounts a on cfh.object_id = a.id and cfh.object_type = 'ACCOUNT'
where 1 = 1
and (
     cfh.account_record_id != a.record_id
  or cfh.account_record_id is null
)
union
select
  'CUSTOM_FIELDS' as table_name
, count(1) count
from custom_fields cf
left outer join accounts a on cf.object_id = a.id and cf.object_type = 'ACCOUNT'
where 1 = 1
and (
     cf.account_record_id != a.record_id
  or cf.account_record_id is null
)
union
select
  'INVOICE_ITEMS' as table_name
, count(1) count
from invoice_items it
left outer join invoices i on it.invoice_id = i.id
left outer join accounts a on i.account_id = a.id
where 1 = 1
and (
     it.account_record_id != a.record_id
  or it.account_record_id is null
)
union
select
  'INVOICE_PAYMENTS' as table_name
, count(1) count
from invoice_payments ip
left outer join invoices i on ip.invoice_id = i.id
left outer join accounts a on i.account_id = a.id
where 1 = 1
and (
     ip.account_record_id != a.record_id
  or ip.account_record_id is null
)
union
select
  'INVOICES' as table_name
, count(1) count
from invoices i
left outer join accounts a on i.account_id = a.id
where 1 = 1
and (
     i.account_record_id != a.record_id
  or i.account_record_id is null
)
union
select
  'NOTIFICATIONS' as table_name
, ifnull(sum(count), 0) count
from (
  select
    class_name
  , count(1) count
  from (
    select
      substr(event_json, 13, 36) id
    , search_key1 account_record_id
    , class_name
    from notifications
    where 1 = 1
    and class_name = 'org.killbill.billing.invoice.notification.NextBillingDateNotificationKey'
  ) n
  left outer join subscriptions s using (id)
  where 1 = 1
  and (
       n.account_record_id is null
    or n.account_record_id != s.account_record_id
  )
  group by class_name
  union all
  select
    class_name
  , count(1) count
  from (
    select
      substr(event_json, 13, 36) id
    , search_key1 account_record_id
    , class_name
    from notifications
    where 1 = 1
    and class_name in ('org.killbill.billing.ovedue.notification.OverdueCheckNotificationKey', 'org.killbill.billing.irs.callbacks.CallbackNotificationKey')
  ) n
  left outer join bundles b using (id)
  where 1 = 1
  and (
       n.account_record_id is null
    or n.account_record_id != b.account_record_id
  )
  group by class_name
  union all
  select
    class_name
  , count(1) count
  from (
    select
      substr(event_json, 13, 36) id
    , search_key1 account_record_id
    , class_name
    from notifications
    where 1 = 1
    and class_name = 'org.killbill.billing.payment.retry.PaymentRetryNotificationKey'
  ) n
  left outer join payments p using (id)
  where 1 = 1
  and (
       n.account_record_id is null
    or n.account_record_id != p.account_record_id
  )
  group by class_name
  union all
  select
    class_name
  , count(1) count
  from (
    select
      substr(event_json, 13, 36) id
    , search_key1 account_record_id
    , class_name
    from notifications
    where 1 = 1
    and class_name = 'org.killbill.billing.entitlement.engine.core.EntitlementNotificationKey'
  ) n
  left outer join subscription_events se using (id)
  where 1 = 1
  and (
       n.account_record_id is null
    or n.account_record_id != se.account_record_id
  )
  group by class_name
) notifications
union
select
  'PAYMENT_ATTEMPTS' as table_name
, count(1) count
from payment_attempts pa
left outer join payments p on pa.payment_id = p.id
left outer join accounts a on p.account_id = a.id
where 1 = 1
and (
     pa.account_record_id != a.record_id
  or pa.account_record_id is null
)
union
select
  'PAYMENT_ATTEMPT_HISTORY' as table_name
, count(1) from payment_attempt_history pah
left outer join payment_attempts pa on pah.target_record_id = pa.record_id
left outer join payments p on pa.payment_id = p.id
left outer join accounts a on p.account_id = a.id
where 1 = 1
and (
     pah.account_record_id != a.record_id
  or pah.account_record_id is null
)
union
select
  'PAYMENT_METHODS' as table_name
, sum(count) count
from (
  select
    count(1) count
  from payment_methods pm
  left outer join accounts a on pm.account_id = a.id
  where 1 = 1
  and (
       pm.account_record_id != a.record_id
    or pm.account_record_id is null
  )
  and pm.is_active = '0'
  union all
  select
    count(1) count
  from payment_methods pm
  left outer join accounts a on pm.account_id = a.id
  where 1 = 1
  and (
       pm.account_record_id != a.record_id
    or pm.account_record_id is null
  )
  and pm.is_active = '1'
) pms
union
select
  'PAYMENTS' as table_name
, count(1) count
from payments p
left outer join accounts a on p.account_id = a.id
where 1 = 1
and (
     p.account_record_id != a.record_id
  or p.account_record_id is null
)
union
select
  'PAYMENT_HISTORY' as table_name
, count(1) count
from payment_history ph
left outer join payments p on ph.target_record_id = p.record_id
where 1 = 1
and (
     ph.account_record_id != p.account_record_id
  or ph.account_record_id is null
)
union
select
  'REFUND_HISTORY' as table_name
, count(1) count
from refund_history rh
left outer join refunds r on rh.target_record_id = r.record_id
left outer join accounts a on r.account_id = a.id
where 1 = 1
and (
     rh.account_record_id != a.record_id
  or rh.account_record_id is null
)
union
select
  'REFUNDS' as table_name
, count(1) count
from refunds r
left outer join accounts a on r.account_id = a.id
where 1 = 1
and (
     r.account_record_id != a.record_id
  or r.account_record_id is null
)
union
select
  'SUBSCRIPTIONS' as table_name
, count(1) count
from subscriptions s
left outer join bundles b on s.bundle_id = b.id
left outer join accounts a on b.account_id = a.id
where 1 = 1
and (
     s.account_record_id != a.record_id
  or s.account_record_id is null
)
union
select
  'SUBSCRIPTION_EVENTS' as table_name
, count(1) count
from subscription_events e
left outer join subscriptions s on e.subscription_id = s.id
left outer join bundles b on s.bundle_id = b.id
left outer join accounts a on b.account_id = a.id
where 1 = 1
and (
     e.account_record_id != a.record_id
  or e.account_record_id is null
)
union
select
  'TAG_HISTORY' as table_name
, count(1) count
from tag_history th
left outer join accounts a on th.object_id = a.id and th.object_type = 'ACCOUNT'
where 1 = 1
and (
     th.account_record_id != a.record_id
  or th.account_record_id is null
)
union
select
  'TAGS' as table_name
, count(1) count
from tags t
left outer join accounts a on t.object_id = a.id and t.object_type = 'ACCOUNT'
where 1 = 1
and (
     t.account_record_id != a.record_id
  or t.account_record_id is null
)
;
