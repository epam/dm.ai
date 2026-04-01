// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bridge;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.polyglot.HostAccess;

/**
 * Provides dynamic API description for DMToolsBridge methods.
 * This class analyzes the DMToolsBridge class and generates documentation
 * that can be injected into AI prompts for JavaScript code generation.
 */
public class DMToolsBridgeAPIProvider {

    /**
     * Generates a comprehensive API description for DMToolsBridge methods
     * based on the specified permissions.
     *
     * @param permissions Set of permissions to filter available methods
     * @return Formatted API description string
     */
    public static String generateAPIDescription(Set<DMToolsBridge.Permission> permissions) {
        StringBuilder apiDoc = new StringBuilder();
        apiDoc.append("# DMToolsBridge API Reference\n\n");
        
        // Add permission context
        apiDoc.append("## Available Permissions\n");
        for (DMToolsBridge.Permission permission : permissions) {
            apiDoc.append("- ").append(permission.name()).append("\n");
        }
        apiDoc.append("\n");

        // Get all public methods with @HostAccess.Export annotation
        Method[] methods = DMToolsBridge.class.getDeclaredMethods();
        List<Method> exportedMethods = Arrays.stream(methods)
            .filter(method -> method.isAnnotationPresent(HostAccess.Export.class))
            .sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
            .collect(Collectors.toList());

        // Group methods by category
        apiDoc.append("## Available Methods\n\n");
        
        List<Method> loggingMethods = filterMethodsByCategory(exportedMethods, "jsLog");
        if (!loggingMethods.isEmpty()) {
            apiDoc.append("### Logging Methods\n");
            for (Method method : loggingMethods) {
                appendMethodDocumentation(apiDoc, method, permissions);
            }
            apiDoc.append("\n");
        }

        List<Method> presentationMethods = filterMethodsByCategory(exportedMethods, "Presentation", "RequestData");
        if (!presentationMethods.isEmpty()) {
            apiDoc.append("### Presentation Methods\n");
            for (Method method : presentationMethods) {
                appendMethodDocumentation(apiDoc, method, permissions);
            }
            apiDoc.append("\n");
        }

        List<Method> reportMethods = filterMethodsByCategory(exportedMethods, "Report", "generate");
        if (!reportMethods.isEmpty()) {
            apiDoc.append("### Reporting Methods\n");
            for (Method method : reportMethods) {
                appendMethodDocumentation(apiDoc, method, permissions);
            }
            apiDoc.append("\n");
        }

        List<Method> trackerMethods = filterMethodsByCategory(exportedMethods, "Tracker", "Client");
        if (!trackerMethods.isEmpty()) {
            apiDoc.append("### Tracker Client Methods\n");
            for (Method method : trackerMethods) {
                appendMethodDocumentation(apiDoc, method, permissions);
            }
            apiDoc.append("\n");
        }

        List<Method> httpMethods = filterMethodsByCategory(exportedMethods, "execute", "Http", "BasePath");
        if (!httpMethods.isEmpty()) {
            apiDoc.append("### HTTP Client Methods\n");
            for (Method method : httpMethods) {
                appendMethodDocumentation(apiDoc, method, permissions);
            }
            apiDoc.append("\n");
        }

        List<Method> utilityMethods = new ArrayList<>(exportedMethods);
        utilityMethods.removeAll(loggingMethods);
        utilityMethods.removeAll(presentationMethods);
        utilityMethods.removeAll(reportMethods);
        utilityMethods.removeAll(trackerMethods);
        utilityMethods.removeAll(httpMethods);
        
        if (!utilityMethods.isEmpty()) {
            apiDoc.append("### Utility Methods\n");
            for (Method method : utilityMethods) {
                appendMethodDocumentation(apiDoc, method, permissions);
            }
            apiDoc.append("\n");
        }

        apiDoc.append("## Usage Notes\n");
        apiDoc.append("- All methods are called on the 'bridge' object passed to your JavaScript function\n");
        apiDoc.append("- Check permissions before calling restricted methods\n");
        apiDoc.append("- Use try-catch blocks for error handling\n");
        apiDoc.append("- Use logging methods to track execution progress\n");
        apiDoc.append("- Handle both synchronous and asynchronous operations appropriately\n");

        return apiDoc.toString();
    }

    /**
     * Generates a simplified API description with just method signatures
     */
    public static String generateSimpleAPIDescription(Set<DMToolsBridge.Permission> permissions) {
        StringBuilder apiDoc = new StringBuilder();
        
        Method[] methods = DMToolsBridge.class.getDeclaredMethods();
        List<Method> exportedMethods = Arrays.stream(methods)
            .filter(method -> method.isAnnotationPresent(HostAccess.Export.class))
            .sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
            .collect(Collectors.toList());

        for (Method method : exportedMethods) {
            apiDoc.append("bridge.").append(method.getName()).append("(");
            
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) apiDoc.append(", ");
                apiDoc.append(getSimpleTypeName(parameters[i].getType()))
                      .append(" ").append(parameters[i].getName());
            }
            
            apiDoc.append("): ").append(getSimpleTypeName(method.getReturnType())).append("\n");
        }
        
        return apiDoc.toString();
    }

    private static List<Method> filterMethodsByCategory(List<Method> methods, String... keywords) {
        return methods.stream()
            .filter(method -> {
                String methodName = method.getName().toLowerCase();
                return Arrays.stream(keywords)
                    .anyMatch(keyword -> methodName.contains(keyword.toLowerCase()));
            })
            .collect(Collectors.toList());
    }

    private static void appendMethodDocumentation(StringBuilder apiDoc, Method method, Set<DMToolsBridge.Permission> permissions) {
        apiDoc.append("#### ").append(method.getName()).append("\n");
        
        // Method signature
        apiDoc.append("**Signature:** `bridge.").append(method.getName()).append("(");
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) apiDoc.append(", ");
            apiDoc.append(getSimpleTypeName(parameters[i].getType()))
                  .append(" ").append(parameters[i].getName());
        }
        apiDoc.append("): ").append(getSimpleTypeName(method.getReturnType())).append("`\n\n");

        // Parameters description
        if (parameters.length > 0) {
            apiDoc.append("**Parameters:**\n");
            for (Parameter param : parameters) {
                apiDoc.append("- `").append(param.getName())
                      .append("` (").append(getSimpleTypeName(param.getType())).append(")")
                      .append(" - ").append(generateParameterDescription(param)).append("\n");
            }
            apiDoc.append("\n");
        }

        // Return value description
        if (!method.getReturnType().equals(void.class)) {
            apiDoc.append("**Returns:** ").append(getSimpleTypeName(method.getReturnType()))
                  .append(" - ").append(generateReturnDescription(method)).append("\n\n");
        }

        // Permission requirement
        String requiredPermission = inferRequiredPermission(method);
        if (requiredPermission != null) {
            apiDoc.append("**Required Permission:** ").append(requiredPermission).append("\n\n");
        }
    }

    private static String getSimpleTypeName(Class<?> type) {
        if (type.isArray()) {
            return getSimpleTypeName(type.getComponentType()) + "[]";
        }
        return type.getSimpleName();
    }

    private static String generateParameterDescription(Parameter param) {
        String paramName = param.getName().toLowerCase();
        String typeName = param.getType().getSimpleName().toLowerCase();
        
        if (paramName.contains("message")) {
            return "Log message to output";
        } else if (paramName.contains("topic")) {
            return "Presentation topic or title";
        } else if (paramName.contains("jql")) {
            return "JQL query string for filtering";
        } else if (paramName.contains("date")) {
            return "Date string in various formats (YYYY-MM-DD, epoch, etc.)";
        } else if (paramName.contains("json")) {
            return "JSON string containing structured data";
        } else if (paramName.contains("config")) {
            return "Configuration object or JSON";
        } else if (typeName.contains("map")) {
            return "Key-value map object";
        } else if (typeName.contains("string")) {
            return "String value";
        } else {
            return "Parameter value";
        }
    }

    private static String generateReturnDescription(Method method) {
        String methodName = method.getName().toLowerCase();
        String returnType = method.getReturnType().getSimpleName().toLowerCase();
        
        if (methodName.contains("generate") && returnType.contains("string")) {
            return "Generated report or content as string";
        } else if (methodName.contains("get") && returnType.contains("string")) {
            return "Retrieved value as string";
        } else if (methodName.contains("create") && returnType.contains("string")) {
            return "Created object serialized as JSON string";
        } else if (returnType.contains("boolean")) {
            return "Success status or validation result";
        } else if (returnType.contains("string")) {
            return "Result as string";
        } else {
            return "Method result";
        }
    }

    private static String inferRequiredPermission(Method method) {
        String methodName = method.getName().toLowerCase();
        
        if (methodName.startsWith("jslog")) {
            if (methodName.contains("info")) return "LOGGING_INFO";
            if (methodName.contains("warn")) return "LOGGING_WARN";
            if (methodName.contains("error")) return "LOGGING_ERROR";
        } else if (methodName.contains("presentation")) {
            return "PRESENTATION_*";
        } else if (methodName.contains("report") || methodName.contains("generate")) {
            return "REPORT_*";
        } else if (methodName.contains("tracker")) {
            return "TRACKER_CLIENT_ACCESS";
        } else if (methodName.contains("execute") && methodName.contains("post")) {
            return "HTTP_POST_REQUESTS";
        } else if (methodName.contains("execute") && methodName.contains("get")) {
            return "HTTP_GET_REQUESTS";
        } else if (methodName.contains("basepath")) {
            return "HTTP_BASE_PATH_ACCESS";
        }
        
        return null;
    }
} 