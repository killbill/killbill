package com.ning.billing.invoice.template.translator;

public interface InvoiceStrings {
    String getInvoiceTitle();
    String getInvoiceDate();
    String getInvoiceNumber();
    String getAccountOwnerName();
    String getAccountOwnerEmail();
    String getAccountOwnerPhone();

    // company name and address
    String getCompanyName();
    String getCompanyAddress();
    String getCompanyCityProvincePostalCode();
    String getCompanyCountry();
    String getCompanyUrl();

    String getInvoiceItemBundleName();
    String getInvoiceItemDescription();
    String getInvoiceItemServicePeriod();
    String getInvoiceItemAmount();

    String getInvoiceAmount();
    String getInvoiceAmountPaid();
    String getInvoiceBalance();
}
