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
package org.apache.brooklyn.tasks.kubectl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Strings;
import org.testng.annotations.Test;

@SuppressWarnings( "UnstableApiUsage")
@Test(groups = {"Live"})
public class DockerSensorTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testEchoPerlCommand() {
        final String message = ("hello " + Strings.makeRandomId(10)).toLowerCase();

        ConfigBag parameters = ConfigBag.newInstance(ImmutableMap.of(
                ContainerCommons.CONTAINER_IMAGE, "perl",
                ContainerCommons.COMMANDS, ImmutableList.of("/bin/bash", "-c","echo " + message) ,
                DockerSensor.SENSOR_PERIOD, "1s",
                DockerSensor.SENSOR_NAME, "test-echo-sensor"));

        DockerSensor<String> initializer = new DockerSensor<>(parameters);
        TestEntity parentEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class).addInitializer(initializer));
        app.start(ImmutableList.of());

        EntityAsserts.assertAttributeEqualsEventually(parentEntity, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEventually(parentEntity, Sensors.newStringSensor("test-echo-sensor"), s -> s.contains(message));
    }

    @Test
    public void testEchoPerlCommandAndArgs() {
        final String message = ("hello " + Strings.makeRandomId(10)).toLowerCase();

        ConfigBag parameters = ConfigBag.newInstance(ImmutableMap.of(
                ContainerCommons.CONTAINER_IMAGE, "perl",
                ContainerCommons.COMMANDS, ImmutableList.of("/bin/bash") ,
                ContainerCommons.ARGUMENTS, ImmutableList.of("-c", "echo " + message) ,
                DockerSensor.SENSOR_PERIOD, "1s",
                DockerSensor.SENSOR_NAME, "test-echo-sensor"));

        DockerSensor<String> initializer = new DockerSensor<>(parameters);
        TestEntity parentEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class).addInitializer(initializer));
        app.start(ImmutableList.of());

        EntityAsserts.assertAttributeEqualsEventually(parentEntity, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEventually(parentEntity, Sensors.newStringSensor("test-echo-sensor"), s -> s.contains(message));
    }

    @Test
    public void testEchoPerlArgs() {
        final String message = ("hello " + Strings.makeRandomId(10)).toLowerCase();

        ConfigBag parameters = ConfigBag.newInstance(ImmutableMap.of(
                ContainerCommons.CONTAINER_IMAGE, "perl",
                ContainerCommons.ARGUMENTS, ImmutableList.of("echo", message) ,
                DockerSensor.SENSOR_PERIOD, "1s",
                DockerSensor.SENSOR_NAME, "test-echo-sensor"));

        DockerSensor<String> initializer = new DockerSensor<>(parameters);
        TestEntity parentEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class).addInitializer(initializer));
        app.start(ImmutableList.of());

        EntityAsserts.assertAttributeEqualsEventually(parentEntity, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEventually(parentEntity, Sensors.newStringSensor("test-echo-sensor"), s -> s.contains(message));
    }

    @Test
    public void testEchoBashArgs() {
        final String message = ("hello " + Strings.makeRandomId(10)).toLowerCase();

        ConfigBag parameters = ConfigBag.newInstance(ImmutableMap.of(
                ContainerCommons.CONTAINER_IMAGE, "bash",
                ContainerCommons.ARGUMENTS, ImmutableList.of("-c", "echo " + message) ,
                DockerSensor.SENSOR_PERIOD, "1s",
                DockerSensor.SENSOR_NAME, "test-echo-sensor"));

        DockerSensor<String> initializer = new DockerSensor<>(parameters);
        TestEntity parentEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class).addInitializer(initializer));
        app.start(ImmutableList.of());

        EntityAsserts.assertAttributeEqualsEventually(parentEntity, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEventually(parentEntity, Sensors.newStringSensor("test-echo-sensor"), s -> s.contains(message));
    }

    @Test
    public void testTfVersionSensor() {
        ConfigBag parameters = ConfigBag.newInstance(ImmutableMap.of(
                ContainerCommons.CONTAINER_IMAGE, "hashicorp/terraform",
                ContainerCommons.COMMANDS, ImmutableList.of("terraform", "version" ),
                DockerSensor.SENSOR_PERIOD, "1s",
                DockerSensor.SENSOR_NAME, "tf-version-sensor"));

        DockerSensor<String> initializer = new DockerSensor<>(parameters);
        TestEntity parentEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class).addInitializer(initializer));
        app.start(ImmutableList.of());

        EntityAsserts.assertAttributeEqualsEventually(parentEntity, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEventually(parentEntity, Sensors.newStringSensor("tf-version-sensor"), s -> s.contains("Terraform"));
    }
}
