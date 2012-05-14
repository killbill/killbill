/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.jaxrs.resources;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.EntitlementRepairException;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.AccountTimelineJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubsciptions;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.jaxrs.util.TagHelper;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.StringCustomField;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;


@Singleton
@Path(BaseJaxrsResource.ACCOUNTS_PATH)
public class AccountResource implements BaseJaxrsResource {

    private static final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final AccountUserApi accountApi;
    private final EntitlementUserApi entitlementApi;
    private final EntitlementTimelineApi timelineApi;
    private final InvoiceUserApi invoiceApi;
    private final PaymentApi paymentApi;
    private final Context context;
    private final TagUserApi tagUserApi;
    private final JaxrsUriBuilder uriBuilder;
    private final TagHelper tagHelper;
    
    @Inject
    public AccountResource(final JaxrsUriBuilder uriBuilder,
            final AccountUserApi accountApi,
            final EntitlementUserApi entitlementApi, 
            final InvoiceUserApi invoiceApi,
            final PaymentApi paymentApi,
            final EntitlementTimelineApi timelineApi,
            final TagUserApi tagUserApi,
            final TagHelper tagHelper,
            final Context context) {
        this.uriBuilder = uriBuilder;
    	this.accountApi = accountApi;
    	this.tagUserApi = tagUserApi;
        this.entitlementApi = entitlementApi;
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
        this.timelineApi = timelineApi;
        this.context = context;
        this.tagHelper = tagHelper;
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getAccount(@PathParam("accountId") String accountId) {
        try {
            Account account = accountApi.getAccountById(UUID.fromString(accountId));

            AccountJson json = new AccountJson(account);
            return Response.status(Status.OK).entity(json).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();            
        }
        
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + BUNDLES)
    @Produces(APPLICATION_JSON)
    public Response getAccountBundles(@PathParam("accountId") String accountId) {
        try {
            UUID uuid = UUID.fromString(accountId);
            accountApi.getAccountById(uuid);

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(uuid);
            Collection<BundleJsonNoSubsciptions> result = Collections2.transform(bundles, new Function<SubscriptionBundle, BundleJsonNoSubsciptions>() {
                @Override
                public BundleJsonNoSubsciptions apply(SubscriptionBundle input) {
                    return new BundleJsonNoSubsciptions(input);
                }
            });
            return Response.status(Status.OK).entity(result).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        }
    }

    
    @GET
    @Produces(APPLICATION_JSON)
    public Response getAccountByKey(@QueryParam(QUERY_EXTERNAL_KEY) String externalKey) {
        try {
            Account account = null;
            if (externalKey != null) {
                account = accountApi.getAccountByKey(externalKey);
            }
            if (account == null) {
                return Response.status(Status.NO_CONTENT).build();
            }
            AccountJson json = new AccountJson(account);
            return Response.status(Status.OK).entity(json).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        }
    }

    
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createAccount(final AccountJson json,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        try {
            AccountData data = json.toAccountData();
            final Account account = accountApi.createAccount(data, null, null, context.createContext(createdBy, reason, comment));
            Response response = uriBuilder.buildResponse(AccountResource.class, "getAccount", account.getId());
            return response;
        } catch (AccountApiException e) {
            final String error = String.format("Failed to create account %s", json);
            log.info(error, e);
            return Response.status(Status.BAD_REQUEST).entity(error).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}")
    public Response updateAccount(final AccountJson json,
            @PathParam("accountId") final String accountId,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {
        try {
            AccountData data = json.toAccountData();
            UUID uuid = UUID.fromString(accountId);
            accountApi.updateAccount(uuid, data, context.createContext(createdBy, reason, comment));
            return getAccount(accountId);
        } catch (AccountApiException e) {
        	if (e.getCode() == ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID.getCode()) {
        		return Response.status(Status.NO_CONTENT).build();        		
        	} else {
        		log.info(String.format("Failed to update account %s with %s", accountId, json), e);
        		return Response.status(Status.BAD_REQUEST).build();
        	}
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    // Not supported
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response cancelAccount(@PathParam("accountId") String accountId) {
        /*
        try {
            accountApi.cancelAccount(accountId);
            return Response.status(Status.NO_CONTENT).build();
        } catch (AccountApiException e) {
            log.info(String.format("Failed to cancel account %s", accountId), e);
            return Response.status(Status.BAD_REQUEST).build();
        }
       */
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + TIMELINE)
    @Produces(APPLICATION_JSON)
    public Response getAccountTimeline(@PathParam("accountId") String accountId) {
        try {
            Account account = accountApi.getAccountById(UUID.fromString(accountId));
           
            List<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId());
            List<PaymentAttempt> payments = new LinkedList<PaymentAttempt>();

            if (invoices.size() > 0) {
                Collection<String> tmp = Collections2.transform(invoices, new Function<Invoice, String>() {
                    @Override
                    public String apply(Invoice input) {
                        return input.getId().toString();
                    }
                });
                List<String> invoicesId = new ArrayList<String>();
                invoicesId.addAll(tmp);
                for (String curId : invoicesId) {
                    payments.addAll(paymentApi.getPaymentAttemptsForInvoiceId(curId));
                }
            }

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(account.getId());
            List<BundleTimeline> bundlesTimeline = new LinkedList<BundleTimeline>();
            for (SubscriptionBundle cur : bundles) {
                bundlesTimeline.add(timelineApi.getBundleRepair(cur.getId()));
            }
            AccountTimelineJson json = new AccountTimelineJson(account, invoices, payments, bundlesTimeline);
            return Response.status(Status.OK).entity(json).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        } catch (PaymentApiException e) {
            log.error(e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } catch (EntitlementRepairException e) {
            log.error(e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    
    /****************************      TAGS     ******************************/
    
    @GET
    @Path(BaseJaxrsResource.TAGS + "/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getAccountTags(@PathParam("accountId") String accountId) {
        try {
            Account account = accountApi.getAccountById(UUID.fromString(accountId));
            List<Tag> tags = account.getTagList();
            Collection<String> tagNameList = (tags.size() == 0) ?
                    Collections.<String>emptyList() :
                Collections2.transform(tags, new Function<Tag, String>() {
                @Override
                public String apply(Tag input) {
                    return input.getTagDefinitionName();
                }
            });
            return Response.status(Status.OK).entity(tagNameList).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        }
    }

    
    @POST
    @Path(BaseJaxrsResource.TAGS + "/{accountId:" + UUID_PATTERN + "}")    
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createAccountTag(@PathParam("accountId") final String accountId,
            @QueryParam(QUERY_TAGS) final String tagList,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        try {
            Preconditions.checkNotNull(tagList, "Query % list cannot be null", QUERY_TAGS);
            
            Account account = accountApi.getAccountById(UUID.fromString(accountId));

            List<TagDefinition> input = tagHelper.getTagDifinitionFromTagList(tagList);
            account.addTagsFromDefinitions(input);
            Response response = uriBuilder.buildResponse(AccountResource.class, "getAccountTags", account.getId());
            return response;
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NullPointerException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (TagDefinitionApiException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
    
    @DELETE
    @Path(BaseJaxrsResource.TAGS +  "/{accountId:" + UUID_PATTERN + "}")    
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteAccountTag(@PathParam("accountId") final String accountId,
            @QueryParam(QUERY_TAGS) final String tagList,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        try {
            Account account = accountApi.getAccountById(UUID.fromString(accountId));

            // Tag APIs needs tome rework...
            String inputTagList = tagList;
            if (inputTagList == null) {
                List<Tag> existingTags = account.getTagList();
                StringBuilder tmp = new StringBuilder();
                for (Tag cur : existingTags) {
                    tmp.append(cur.getTagDefinitionName());
                    tmp.append(",");
                }
                inputTagList = tmp.toString();
            }

            List<TagDefinition> input = tagHelper.getTagDifinitionFromTagList(tagList);   
            for (TagDefinition cur : input) {
                account.removeTag(cur);
            }

            return Response.status(Status.OK).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NullPointerException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (TagDefinitionApiException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
    
    /************************   CUSTOM FIELDS   ******************************/
    
    @GET
    @Path(BaseJaxrsResource.CUSTOM_FIELDS + "/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getAccountCustomFields(@PathParam("accountId") String accountId) {
        try {
            Account account = accountApi.getAccountById(UUID.fromString(accountId));
            List<CustomField> fields = account.getFieldList();
            List<CustomFieldJson> result = new LinkedList<CustomFieldJson>();
            for (CustomField cur : fields) {
                result.add(new CustomFieldJson(cur));
            }
            return Response.status(Status.OK).entity(result).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        }
    }
    
    
    @POST
    @Path(BaseJaxrsResource.CUSTOM_FIELDS + "/{accountId:" + UUID_PATTERN + "}")    
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCustomField(@PathParam("accountId") final String accountId,
            List<CustomFieldJson> customFields,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        try {
            
            Account account = accountApi.getAccountById(UUID.fromString(accountId));
            LinkedList<CustomField> input = new LinkedList<CustomField>();
            for (CustomFieldJson cur : customFields) {
                input.add(new StringCustomField(cur.getName(), cur.getValue()));
            }
            account.saveFields(input, context.createContext(createdBy, reason, comment));
            Response response = uriBuilder.buildResponse(AccountResource.class, "getAccountCustomFields", account.getId());            
            return response;
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NullPointerException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
    
    @DELETE
    @Path(BaseJaxrsResource.CUSTOM_FIELDS +  "/{accountId:" + UUID_PATTERN + "}")    
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCustomFields(@PathParam("accountId") final String accountId,
            @QueryParam(QUERY_CUSTOM_FIELDS) final String cutomFieldList,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        try {
            Account account = accountApi.getAccountById(UUID.fromString(accountId));
            // STEPH missing API to delete custom fields
            return Response.status(Status.OK).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NullPointerException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
    
}
