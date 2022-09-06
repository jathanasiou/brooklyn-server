/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.workflow;

import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.resolve.jackson.BeanWithTypeUtils;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;

import java.util.Map;

public class WorkflowStepResolution {

    static Map<String,WorkflowStepDefinition> resolveSteps(ManagementContext mgmt, Map<String,Object> steps) {
        Map<String,WorkflowStepDefinition> result = MutableMap.of();
        if (steps==null || steps.isEmpty()) throw new IllegalStateException("No steps defined in workflow");
        steps.forEach((name, def) -> result.put(name, resolveStep(mgmt, name, def)));
        return result;
    }

    static WorkflowStepDefinition resolveStep(ManagementContext mgmt, String name, Object def) {
        BrooklynClassLoadingContext loader = RegisteredTypes.getCurrentClassLoadingContextOrManagement(mgmt);

        if (def instanceof Map) {
            Map<String,Object> map = (Map<String,Object>) def;
            if (map.size()==1 && !map.containsKey("type")) {
                // shorthand definition. use string constructor.
                Map.Entry<String, Object> ent = map.entrySet().iterator().next();
                def = MutableMap.of("type", ent.getKey(), "shorthandValue", ent.getValue());
            }
        }
        try {
            def = BeanWithTypeUtils.convert(mgmt, def, TypeToken.of(WorkflowStepDefinition.class), true, loader, false);
        } catch (Exception e) {
            throw Exceptions.propagateAnnotated("Unable to resolve step "+ name, e);
        }
        if (def instanceof WorkflowStepDefinition) {
            return (WorkflowStepDefinition) def;
        } else {
            throw new IllegalArgumentException("Unable to resolve step "+ name +"; unexpected object "+ def);
        }
    }

}
