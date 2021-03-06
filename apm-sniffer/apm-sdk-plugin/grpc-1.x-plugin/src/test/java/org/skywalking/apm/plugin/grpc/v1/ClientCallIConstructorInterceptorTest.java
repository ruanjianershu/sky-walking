/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.grpc.v1;

import io.grpc.MethodDescriptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MethodDescriptor.class)
public class ClientCallIConstructorInterceptorTest {

    private ClientCallIConstructorInterceptor constructorInterceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Object[] arguments;

    @Before
    public void setUp() {
        constructorInterceptor = new ClientCallIConstructorInterceptor();

        MethodDescriptor methodDescriptor = mock(MethodDescriptor.class);
        when(methodDescriptor.getType()).thenReturn(MethodDescriptor.MethodType.UNARY);
        when(methodDescriptor.getFullMethodName()).thenReturn("test/testMethod");

        arguments = new Object[] {methodDescriptor};
    }

    @Test
    public void testOnConstructor() {
        constructorInterceptor.onConstruct(enhancedInstance, arguments);
        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField(Matchers.any());
    }

}
