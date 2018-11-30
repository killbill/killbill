/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import org.killbill.billing.BillingExceptionBase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.swagger.annotations.ApiModel;

// Doesn't extend JsonBase (no audit logs)
@ApiModel(value="BillingException")
public class BillingExceptionJson {

    private final String className;
    private final Integer code;
    private final String message;
    private final String causeClassName;
    private final String causeMessage;
    private final List<StackTraceElementJson> stackTrace;
    // TODO add getSuppressed() from 1.7?

    @JsonCreator
    public BillingExceptionJson(@JsonProperty("className") final String className,
                                @JsonProperty("code") @Nullable final Integer code,
                                @JsonProperty("message") final String message,
                                @JsonProperty("causeClassName") final String causeClassName,
                                @JsonProperty("causeMessage") final String causeMessage,
                                @JsonProperty("stackTrace") final List<StackTraceElementJson> stackTrace) {
        this.className = className;
        this.code = code;
        this.message = message;
        this.causeClassName = causeClassName;
        this.causeMessage = causeMessage;
        this.stackTrace = stackTrace;
    }

    public BillingExceptionJson(final Exception exception, final boolean withStackTrace) {
        this(exception.getClass().getName(),
             exception instanceof BillingExceptionBase ? ((BillingExceptionBase) exception).getCode() : null,
             exception.getLocalizedMessage(),
             exception.getCause() == null ? null : exception.getCause().getClass().getName(),
             exception.getCause() == null ? null : exception.getCause().getLocalizedMessage(),
             !withStackTrace ? ImmutableList.<StackTraceElementJson>of() :
             Lists.<StackTraceElement, StackTraceElementJson>transform(ImmutableList.<StackTraceElement>copyOf(exception.getStackTrace()),
                                                                       new Function<StackTraceElement, StackTraceElementJson>() {
                                                                           @Override
                                                                           public StackTraceElementJson apply(final StackTraceElement input) {
                                                                               return new StackTraceElementJson(input.getClassName(),
                                                                                                                input.getFileName(),
                                                                                                                input.getLineNumber(),
                                                                                                                input.getMethodName(),
                                                                                                                input.isNativeMethod());
                                                                           }
                                                                       }));
    }

    public String getClassName() {
        return className;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getCauseClassName() {
        return causeClassName;
    }

    public String getCauseMessage() {
        return causeMessage;
    }

    public List<StackTraceElementJson> getStackTrace() {
        return stackTrace;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BillingExceptionJson{");
        sb.append("className='").append(className).append('\'');
        sb.append(", code=").append(code);
        sb.append(", message='").append(message).append('\'');
        sb.append(", causeClassName='").append(causeClassName).append('\'');
        sb.append(", causeMessage='").append(causeMessage).append('\'');
        sb.append(", stackTrace='").append(stackTrace).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BillingExceptionJson that = (BillingExceptionJson) o;

        if (causeClassName != null ? !causeClassName.equals(that.causeClassName) : that.causeClassName != null) {
            return false;
        }
        if (causeMessage != null ? !causeMessage.equals(that.causeMessage) : that.causeMessage != null) {
            return false;
        }
        if (className != null ? !className.equals(that.className) : that.className != null) {
            return false;
        }
        if (code != null ? !code.equals(that.code) : that.code != null) {
            return false;
        }
        if (message != null ? !message.equals(that.message) : that.message != null) {
            return false;
        }
        if (stackTrace != null ? !stackTrace.equals(that.stackTrace) : that.stackTrace != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (causeClassName != null ? causeClassName.hashCode() : 0);
        result = 31 * result + (causeMessage != null ? causeMessage.hashCode() : 0);
        result = 31 * result + (stackTrace != null ? stackTrace.hashCode() : 0);
        return result;
    }

    public static final class StackTraceElementJson {

        private final String className;
        private final String fileName;
        private final Integer lineNumber;
        private final String methodName;
        private final Boolean isNativeMethod;

        @JsonCreator
        public StackTraceElementJson(@JsonProperty("className") final String className,
                                     @JsonProperty("fileName") final String fileName,
                                     @JsonProperty("lineNumber") final Integer lineNumber,
                                     @JsonProperty("methodName") final String methodName,
                                     @JsonProperty("isNativeMethod") final Boolean isNativeMethod) {
            this.className = className;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
            this.methodName = methodName;
            this.isNativeMethod = isNativeMethod;
        }

        public String getClassName() {
            return className;
        }

        public String getFileName() {
            return fileName;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        public String getMethodName() {
            return methodName;
        }

        @JsonProperty("isNativeMethod")
        public Boolean isNativeMethod() {
            return isNativeMethod;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("StackTraceElementJson{");
            sb.append("className='").append(className).append('\'');
            sb.append(", fileName='").append(fileName).append('\'');
            sb.append(", lineNumber=").append(lineNumber);
            sb.append(", methodName='").append(methodName).append('\'');
            sb.append(", isNativeMethod=").append(isNativeMethod);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final StackTraceElementJson that = (StackTraceElementJson) o;

            if (className != null ? !className.equals(that.className) : that.className != null) {
                return false;
            }
            if (fileName != null ? !fileName.equals(that.fileName) : that.fileName != null) {
                return false;
            }
            if (lineNumber != null ? !lineNumber.equals(that.lineNumber) : that.lineNumber != null) {
                return false;
            }
            if (methodName != null ? !methodName.equals(that.methodName) : that.methodName != null) {
                return false;
            }
            if (isNativeMethod != null ? !isNativeMethod.equals(that.isNativeMethod) : that.isNativeMethod != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = className != null ? className.hashCode() : 0;
            result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
            result = 31 * result + (lineNumber != null ? lineNumber.hashCode() : 0);
            result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
            result = 31 * result + (isNativeMethod != null ? isNativeMethod.hashCode() : 0);
            return result;
        }
    }
}
