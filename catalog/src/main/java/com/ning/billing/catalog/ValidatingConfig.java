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

package com.ning.billing.catalog;

import java.net.URL;
import java.util.ArrayList;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class ValidatingConfig {
	public static class ValidationErrors extends ArrayList<ValidationError>{
		private static final long serialVersionUID = 1L;

		public void add(String description, URL catalogURL,
				Class<? extends ValidatingConfig> objectType, String objectName) {
			add(new ValidationError(description, catalogURL, objectType, objectName));
			
		}

	}

	public abstract ValidationErrors validate(Catalog catalog, ValidationErrors errors);

	public ValidationErrors validate() {
		if(!(this instanceof Catalog)) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("Root type was not ICatalog", null, this.getClass(), null);
			return errors;
		}
		return validate((Catalog)this, new ValidationErrors());
	}

	protected ValidationErrors validate(Catalog catalog, ValidationErrors errors, ValidatingConfig[] configs) {
		for(ValidatingConfig c : configs) {
			errors.addAll(c.validate(catalog, errors));
		}
		return errors;
	}
	
	public void initialize(Catalog catalog) {
		
	}

}
