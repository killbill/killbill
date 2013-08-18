package com.ning.billing.jaxrs.mappers;

import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.ning.billing.ErrorCode;
import com.ning.billing.entitlement.api.EntitlementApiException;

@Singleton
@Provider
public class EntitlementApiExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<EntitlementApiException> {

    private final UriInfo uriInfo;

    public EntitlementApiExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final EntitlementApiException exception) {
        if (exception.getCode() == ErrorCode.SUB_CANCEL_BAD_STATE.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CHANGE_NON_ACTIVE.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_INVALID_SUBSCRIPTION_ID.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else {
            return buildBadRequestResponse(exception, uriInfo);
        }
    }
}
