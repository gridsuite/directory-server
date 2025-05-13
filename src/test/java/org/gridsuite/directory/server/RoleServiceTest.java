/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import jakarta.servlet.http.HttpServletRequest;
import org.gridsuite.directory.server.constants.ApplicationRoles;
import org.gridsuite.directory.server.services.RoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import static org.gridsuite.directory.server.services.RoleService.ROLES_HEADER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Achour Berrahma <achour.berrahma at rte-france.com>
 */
@SpringBootTest(classes = {DirectoryApplication.class})
class RoleServiceTest {

    @InjectMocks
    private RoleService roleService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private ServletRequestAttributes attributes;

    @BeforeEach
    void setUp() {
        // Set up the RequestContextHolder with our mocked ServletRequestAttributes
        when(attributes.getRequest()).thenReturn(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDown() {
        // Clear the RequestContextHolder after each test
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testHasRequiredRolesWithNoMatchingRolesAllRequired() {
        // Test when the user has none of the required roles (all required)
        when(request.getHeader(ROLES_HEADER)).thenReturn("VIEWER|GUEST");

        boolean hasRoles = roleService.hasRequiredRoles(Set.of("ADMIN", "USER"), true);

        assertFalse(hasRoles);
        verify(request).getHeader(ROLES_HEADER);
    }

    @Test
    void testCheckAccessWhenUserHasRequiredRoles() {
        when(request.getHeader(ROLES_HEADER)).thenReturn("ADMIN_EXPLORE|USER");
        assertTrue(roleService.isUserExploreAdmin());
        assertTrue(roleService.hasRequiredRoles(Set.of("ADMIN_EXPLORE", "GUEST"), false));
    }

    @Test
    void testCheckAccessWhenUserDoesNotHaveRequiredRoles() {
        when(request.getHeader(ROLES_HEADER)).thenReturn("USER|ADMIN");
        assertFalse(roleService.isUserExploreAdmin());
        assertFalse(roleService.hasRequiredRoles(Set.of("ADMIN_EXPLORE", "GUEST"), false));
    }

    @Test
    void testCheckAccessWhenRolesHeaderIsEmpty() {
        when(request.getHeader(ROLES_HEADER)).thenReturn("");

        boolean hasRoles = roleService.hasRequiredRoles(Set.of("ADMIN", "USER"), true);

        assertFalse(hasRoles);
        verify(request).getHeader(ROLES_HEADER);
    }

    @Test
    void privateConstructor_shouldThrowAssertionError() throws Exception {
        Constructor<ApplicationRoles> constructor = ApplicationRoles.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Expected AssertionError was not thrown");
        } catch (InvocationTargetException ex) {
            // Unwrap and assert the cause
            Throwable cause = ex.getCause();
            assertInstanceOf(AssertionError.class, cause);
            assertEquals("Utility class should not be instantiated", cause.getMessage());
        }
    }

}
