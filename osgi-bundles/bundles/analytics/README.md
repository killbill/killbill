Analytics plugin
================

The Analytics plugin provides simple, yet powerful, dashboarding capabilities.

To create a dashboard, go to (http://127.0.0.1:8080/plugins/killbill-analytics/static/analytics.html).

A dashboard is constituted of a number of reports, each of them being numbered, starting from 1. All reports are displayed in a single column, the report number 1 being the top most one. All reports share the same X axis.

Each report can contain one or multiple time series, the source data being a table or a view with the following format:

<table>
  <tr>
    <th>SQL column name</th><th>Description</th><th></th>
  </tr>
  <tr>
    <td>pivot</td><td>Subcategory in your data</td><td>Optional</td>
  </tr>
  <tr>
    <td>day</td><td>X values (date or datetime)</td><td>Required</td>
  </tr>
  <tr>
    <td>count</td><td>Y values (float)</td><td>Required</td>
  </tr>
</table>

To configure a report, create a INI file in the following format:

    [report_name]
    tableName = view_or_table_name_to_query
    prettyName = Pretty name to use for the dashboard legend
    # Optional, specify a refresh schedule (via a stored procedure)
    #storedProcedureName = refresh_my_report
    #frequency = DAILY
    #refreshTimeOfTheDayGMT = 5

The path to the INI file can be configured via -Dcom.ning.billing.osgi.bundles.analytics.reports.configuration

API
---

The dashboard system is controlled by query parameters:

* **report1**, **report2**, etc.: report name (from the configuration). The number determines in which slot the data should be displayed, starting from the top of the page. For example, report1=trials&report1=conversions&report1=cancellations&report2=accounts will graph the trials, conversions and cancellations reports in the first slot (on the same graph), and the accounts report below (in slot 2)
* **startDate** and **endDate**: dates to filter the data on the server side. For example: startDate=2012-08-01&endDate=2013-10-01
* **smooth1**, **smooth2**, etc.: smoothing function to apply for data in a given slot. Currently support smoothing functions are:
 * AVERAGE\_WEEKLY: average the values on a weekly basis
 * AVERAGE\_MONTHLY: average the values on a monthly basis
 * SUM\_WEEKLY: sum all values on a weekly basis
 * SUM\_MONTHLY: sum all values on a monthly basis
* To filter pivots from a report, use *!* for exclusions and *$* for inclusions. For example, report1=payments_per_day$AUD$EUR will graph the payments for AUD and EUR only, whereas report1=payments_per_day!AUD!EUR will graph all payments but the ones in AUD and EUR.
