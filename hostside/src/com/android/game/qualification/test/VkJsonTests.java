/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.game.qualification.test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(DeviceJUnit4ClassRunner.class)
public class VkJsonTests extends BaseHostJUnit4Test {
    // *** BEGIN CLASSES FOR GSON ***
    // Classes to be used with GSON.  The structure follows the output of 'cmd gpu vkjson', which is
    // format is defined in /frameworks/native/vulkan/vkjson/vkjson.cc
    private static class VkJson {
        List<VkJsonDevice> devices;
    }

    private static class VkJsonDevice {
        VkJsonExtDriverProperties VK_KHR_driver_properties;
        List<VkExtension> extensions;
    }

    private static class VkJsonExtDriverProperties {
        VkPhysicalDeviceDriverPropertiesKHR driverPropertiesKHR;
    }

    private static class VkPhysicalDeviceDriverPropertiesKHR {
        Long driverID;
        String driverName;
        String driverInfo;
        VkConformanceVersionKHR conformanceVersion;
    }

    private static class VkConformanceVersionKHR {
        Short major;
        Short minor;
        Short subminor;
        Short patch;
    }

    private static class VkExtension {
        String extensionName;
        Integer specVersion;
    }
    // *** END CLASSES FOR GSON ***

    private VkJson mVkJson;

    @Before
    public void setUp() throws DeviceNotAvailableException {
        String cmdString = getDevice().executeShellCommand("cmd gpu vkjson");
        Gson gson = new Gson();
        mVkJson = gson.fromJson(cmdString, VkJson.class);

        assertThat(mVkJson.devices).isNotNull();
        assertThat(mVkJson.devices).isNotEmpty();
    }

    @Test
    public void checkRequiredExtensions() {
        final Collection<String> REQUIRED_EXTENSIONS = Arrays.asList(
                "VK_GOOGLE_display_timing",
                "VK_KHR_driver_properties");

        List<String> extensions = mVkJson.devices.get(0).extensions.stream()
                .map(it -> it.extensionName)
                .collect(Collectors.toList());
        assertWithMessage("Required Vulkan extensions are not supported")
                .that(extensions)
                .named("supported extensions")
                .containsAllIn(REQUIRED_EXTENSIONS);
    }

    @Test
    public void checkKHRDriverProperties() {
        // Check driver conformance version is at least 1.1.2.
        final short MAJOR = 1;
        final short MINOR = 1;
        final short SUBMINOR = 2;
        final String DRIVER_CONFORMANCE_VERSION = MAJOR + "." + MINOR + "." + SUBMINOR;

        assertWithMessage("VK_KHR_driver_properties is not supported")
                .that(mVkJson.devices.get(0).VK_KHR_driver_properties)
                .named("VK_KHR_driver_properties")
                .isNotNull();

        VkPhysicalDeviceDriverPropertiesKHR properties =
                mVkJson.devices.get(0).VK_KHR_driver_properties.driverPropertiesKHR;
        assertWithMessage("VK_KHR_driver_properties is not supported")
                .that(properties).named("driverPropertiesKHR").isNotNull();

        VkConformanceVersionKHR version = properties.conformanceVersion;
        assertThat(version).named("driverPropertiesKHR.conformanceVersion").isNotNull();

        String msg = "Driver conformance version must be at least " + DRIVER_CONFORMANCE_VERSION;
        assertWithMessage(msg).that(version.major).named("major version").isAtLeast(MAJOR);
        if (version.major == MAJOR) {
            assertWithMessage(msg).that(version.minor).named("minor version").isAtLeast(MINOR);
            if (version.minor == MINOR) {
                assertWithMessage(msg).that(version.subminor).named("subminor version")
                        .isAtLeast(SUBMINOR);
            }
        }
    }
}