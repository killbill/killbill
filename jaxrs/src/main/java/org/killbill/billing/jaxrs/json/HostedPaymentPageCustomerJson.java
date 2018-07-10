/*
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="HostedPaymentPageCustomer", parent = JsonBase.class)
public class HostedPaymentPageCustomerJson extends JsonBase {

    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;

    @JsonCreator
    public HostedPaymentPageCustomerJson(@JsonProperty("firstName") final String firstName,
                                         @JsonProperty("lastName") final String lastName,
                                         @JsonProperty("email") final String email,
                                         @JsonProperty("phone") final String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("HostedPaymentPageCustomerJson{");
        sb.append("firstName='").append(firstName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", email='").append(email).append('\'');
        sb.append(", phone='").append(phone).append('\'');
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

        final HostedPaymentPageCustomerJson that = (HostedPaymentPageCustomerJson) o;

        if (email != null ? !email.equals(that.email) : that.email != null) {
            return false;
        }
        if (firstName != null ? !firstName.equals(that.firstName) : that.firstName != null) {
            return false;
        }
        if (lastName != null ? !lastName.equals(that.lastName) : that.lastName != null) {
            return false;
        }
        if (phone != null ? !phone.equals(that.phone) : that.phone != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = firstName != null ? firstName.hashCode() : 0;
        result = 31 * result + (lastName != null ? lastName.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (phone != null ? phone.hashCode() : 0);
        return result;
    }
}
