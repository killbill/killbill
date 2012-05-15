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

package com.ning.billing.util.glue;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;

import com.google.inject.BindingAnnotation;

/**
 * This annotation is used to bing classes that are being intercepted in junction.
 * 
 * The real implementation of the class is bound in Guice with this parameter, the Blocking implementation
 * is left unannotated.
 *
 */
@BindingAnnotation @Target({ FIELD, PARAMETER, METHOD,LOCAL_VARIABLE }) @Retention(RUNTIME)
public @interface RealImplementation {

}
