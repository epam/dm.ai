package com.github.istin.dmtools.mcp.processor;

import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPParameterDefinition;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPToolDefinition;
import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Annotation processor for @MCPTool annotations.
 * Generates static registry, executor, and schema generator classes at compile time.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("com.github.istin.dmtools.mcp.MCPTool")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class MCPToolProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        List<MCPToolDefinition> toolDefinitions = new ArrayList<>();

        // Scan for @MCPTool annotations
        for (Element element : roundEnv.getElementsAnnotatedWith(MCPTool.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "@MCPTool can only be applied to methods", element);
                continue;
            }

            ExecutableElement method = (ExecutableElement) element;
            MCPTool annotation = method.getAnnotation(MCPTool.class);
            
            try {
                MCPToolDefinition toolDefinition = createToolDefinition(method, annotation);
                toolDefinitions.add(toolDefinition);
                messager.printMessage(Diagnostic.Kind.NOTE, 
                    "Found MCP tool: " + toolDefinition.getName());
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "Error processing MCP tool: " + e.getMessage(), element);
            }
        }

        if (!toolDefinitions.isEmpty()) {
            try {
                generateToolRegistry(toolDefinitions);
                generateToolExecutor(toolDefinitions);
                generateSchemaGenerator(toolDefinitions);
                messager.printMessage(Diagnostic.Kind.NOTE, 
                    "Generated MCP infrastructure for " + toolDefinitions.size() + " tools");
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "Failed to generate MCP infrastructure: " + e.getMessage());
            }
        }

        return true;
    }

    private MCPToolDefinition createToolDefinition(ExecutableElement method, MCPTool annotation) {
        TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
        String className = enclosingClass.getQualifiedName().toString();
        String methodName = method.getSimpleName().toString();
        String returnType = method.getReturnType().toString();

        List<MCPParameterDefinition> parameters = new ArrayList<>();
        List<? extends VariableElement> methodParams = method.getParameters();

        for (int i = 0; i < methodParams.size(); i++) {
            VariableElement param = methodParams.get(i);
            MCPParam paramAnnotation = param.getAnnotation(MCPParam.class);
            
            if (paramAnnotation != null) {
                String paramName = paramAnnotation.name();
                String description = paramAnnotation.description();
                boolean required = paramAnnotation.required();
                String example = paramAnnotation.example();
                String type = paramAnnotation.type();
                String javaType = param.asType().toString();

                MCPParameterDefinition paramDef = new MCPParameterDefinition(
                    paramName, description, required, example, type, javaType, i
                );
                parameters.add(paramDef);
            } else {
                // Create default parameter definition for unannotated parameters
                String paramName = param.getSimpleName().toString();
                String javaType = param.asType().toString();
                MCPParameterDefinition paramDef = new MCPParameterDefinition(
                    paramName, "Parameter " + paramName, true, "", "", javaType, i
                );
                parameters.add(paramDef);
            }
        }

        return new MCPToolDefinition(
            annotation.name(),
            annotation.description(),
            annotation.integration(),
            annotation.category(),
            className,
            methodName,
            returnType,
            parameters
        );
    }

    private void generateToolRegistry(List<MCPToolDefinition> tools) throws IOException {
        JavaFileObject builderFile = filer.createSourceFile("com.github.istin.dmtools.mcp.generated.MCPToolRegistry");
        
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.println("package com.github.istin.dmtools.mcp.generated;");
            out.println();
            out.println("import com.github.istin.dmtools.mcp.MCPToolDefinition;");
            out.println("import com.github.istin.dmtools.mcp.MCPParameterDefinition;");
            out.println("import java.util.*;");
            out.println("import java.util.stream.Collectors;");
            out.println();
            out.println("/**");
            out.println(" * Auto-generated MCP tool registry.");
            out.println(" * Contains all available MCP tools and their metadata.");
            out.println(" */");
            out.println("public class MCPToolRegistry {");
            out.println();
            out.println("    private static final Map<String, MCPToolDefinition> TOOLS = createToolsMap();");
            out.println();
            out.println("    private static Map<String, MCPToolDefinition> createToolsMap() {");
            out.println("        Map<String, MCPToolDefinition> tools = new HashMap<>();");
            
            for (MCPToolDefinition tool : tools) {
                out.println("        tools.put(\"" + tool.getName() + "\", " + generateToolDefinitionCode(tool) + ");");
            }
            
            out.println("        return Collections.unmodifiableMap(tools);");
            out.println("    }");
            out.println();
            out.println("    public static List<MCPToolDefinition> getAllTools() {");
            out.println("        return new ArrayList<>(TOOLS.values());");
            out.println("    }");
            out.println();
            out.println("    public static List<MCPToolDefinition> getToolsForIntegrations(Set<String> integrationTypes) {");
            out.println("        return TOOLS.values().stream()");
            out.println("                .filter(tool -> integrationTypes.contains(tool.getIntegration()))");
            out.println("                .collect(Collectors.toList());");
            out.println("    }");
            out.println();
            out.println("    public static MCPToolDefinition getTool(String toolName) {");
            out.println("        return TOOLS.get(toolName);");
            out.println("    }");
            out.println();
            out.println("    public static boolean hasTool(String toolName) {");
            out.println("        return TOOLS.containsKey(toolName);");
            out.println("    }");
            out.println();
            out.println("    public static Set<String> getAvailableIntegrations() {");
            out.println("        return TOOLS.values().stream()");
            out.println("                .map(MCPToolDefinition::getIntegration)");
            out.println("                .collect(Collectors.toSet());");
            out.println("    }");
            out.println("}");
        }
    }

    private String generateToolDefinitionCode(MCPToolDefinition tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("new MCPToolDefinition(");
        sb.append("\"").append(tool.getName()).append("\", ");
        sb.append("\"").append(escapeJavaString(tool.getDescription())).append("\", ");
        sb.append("\"").append(tool.getIntegration()).append("\", ");
        sb.append("\"").append(tool.getCategory()).append("\", ");
        sb.append("\"").append(tool.getClassName()).append("\", ");
        sb.append("\"").append(tool.getMethodName()).append("\", ");
        sb.append("\"").append(tool.getReturnType()).append("\", ");
        sb.append("Arrays.asList(");
        
        for (int i = 0; i < tool.getParameters().size(); i++) {
            if (i > 0) sb.append(", ");
            MCPParameterDefinition param = tool.getParameters().get(i);
            sb.append("new MCPParameterDefinition(");
            sb.append("\"").append(param.getName()).append("\", ");
            sb.append("\"").append(escapeJavaString(param.getDescription())).append("\", ");
            sb.append(param.isRequired()).append(", ");
            sb.append("\"").append(escapeJavaString(param.getExample())).append("\", ");
            sb.append("\"").append(param.getType()).append("\", ");
            sb.append("\"").append(param.getJavaType()).append("\", ");
            sb.append(param.getParameterIndex());
            sb.append(")");
        }
        
        sb.append("))");
        return sb.toString();
    }

    private void generateToolExecutor(List<MCPToolDefinition> tools) throws IOException {
        JavaFileObject builderFile = filer.createSourceFile("com.github.istin.dmtools.mcp.generated.MCPToolExecutor");
        
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.println("package com.github.istin.dmtools.mcp.generated;");
            out.println();
            out.println("import com.github.istin.dmtools.mcp.MCPToolDefinition;");
            out.println("import com.github.istin.dmtools.mcp.MCPParameterDefinition;");
            out.println("import java.util.*;");
            out.println("import java.lang.reflect.Method;");
            out.println("import org.json.JSONArray;");
            out.println("import org.json.JSONObject;");
            out.println();
            out.println("/**");
            out.println(" * Auto-generated MCP tool executor.");
            out.println(" * Provides type-safe execution of MCP tools.");
            out.println(" */");
            out.println("public class MCPToolExecutor {");
            out.println();
            out.println("    public static Object executeTool(String toolName, Map<String, Object> arguments,");
            out.println("                                    Map<String, Object> clientInstances) throws Exception {");
            out.println("        switch (toolName) {");
            
            for (MCPToolDefinition tool : tools) {
                out.println("            case \"" + tool.getName() + "\":");
                out.println("                return execute" + toCamelCase(tool.getName()) + "(arguments, clientInstances.get(\"" + tool.getIntegration() + "\"));");
            }
            
            out.println("            default:");
            out.println("                throw new IllegalArgumentException(\"Unknown tool: \" + toolName);");
            out.println("        }");
            out.println("    }");
            out.println();
            
            // Generate individual tool execution methods
            for (MCPToolDefinition tool : tools) {
                generateToolExecutionMethod(out, tool);
            }
            
            // Generate parameter conversion helper method
            generateParameterConversionMethod(out);
            
            out.println("}");
        }
    }

    private void generateToolExecutionMethod(PrintWriter out, MCPToolDefinition tool) {
        String methodName = "execute" + toCamelCase(tool.getName());
        out.println("    private static Object " + methodName + "(Map<String, Object> args, Object clientInstance) throws Exception {");
        out.println("        " + tool.getClassName() + " client = (" + tool.getClassName() + ") clientInstance;");
        
        // Generate parameter extraction and conversion
        for (MCPParameterDefinition param : tool.getParameters()) {
            String paramName = param.getName();
            String javaType = param.getJavaType();
            boolean isPrimitive = isPrimitiveType(javaType);
            
            if (param.isRequired()) {
                if (isPrimitive) {
                    out.println("        " + javaType + " " + paramName + " = convertParameter(args.get(\"" + paramName + "\"), \"" + javaType + "\", \"" + paramName + "\");");
                    out.println("        if (args.get(\"" + paramName + "\") == null) {");
                    out.println("            throw new IllegalArgumentException(\"Required parameter '" + paramName + "' is missing\");");
                    out.println("        }");
                } else {
                    out.println("        " + javaType + " " + paramName + " = convertParameter(args.get(\"" + paramName + "\"), \"" + javaType + "\", \"" + paramName + "\");");
                    out.println("        if (" + paramName + " == null) {");
                    out.println("            throw new IllegalArgumentException(\"Required parameter '" + paramName + "' is missing\");");
                    out.println("        }");
                }
            } else {
                out.println("        " + javaType + " " + paramName + " = convertParameter(args.get(\"" + paramName + "\"), \"" + javaType + "\", \"" + paramName + "\");");
            }
        }
        
        // Check if method returns void
        boolean isVoid = "void".equals(tool.getReturnType());
        
        // Generate method call
        if (isVoid) {
            out.print("        client." + tool.getMethodName() + "(");
            for (int i = 0; i < tool.getParameters().size(); i++) {
                if (i > 0) out.print(", ");
                out.print(tool.getParameters().get(i).getName());
            }
            out.println(");");
            out.println("        return null;");
        } else {
            out.print("        return client." + tool.getMethodName() + "(");
            for (int i = 0; i < tool.getParameters().size(); i++) {
                if (i > 0) out.print(", ");
                out.print(tool.getParameters().get(i).getName());
            }
            out.println(");");
        }
        out.println("    }");
        out.println();
    }

    private void generateSchemaGenerator(List<MCPToolDefinition> tools) throws IOException {
        JavaFileObject builderFile = filer.createSourceFile("com.github.istin.dmtools.mcp.generated.MCPSchemaGenerator");
        
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.println("package com.github.istin.dmtools.mcp.generated;");
            out.println();
            out.println("import java.util.*;");
            out.println();
            out.println("/**");
            out.println(" * Auto-generated MCP schema generator.");
            out.println(" * Generates MCP protocol-compliant tool schemas.");
            out.println(" */");
            out.println("public class MCPSchemaGenerator {");
            out.println();
            out.println("    public static Map<String, Object> generateToolsListResponse(Set<String> userIntegrations) {");
            out.println("        List<Map<String, Object>> tools = new ArrayList<>();");
            out.println();
            
            // Group tools by integration
            Map<String, List<MCPToolDefinition>> toolsByIntegration = new HashMap<>();
            for (MCPToolDefinition tool : tools) {
                toolsByIntegration.computeIfAbsent(tool.getIntegration(), k -> new ArrayList<>()).add(tool);
            }
            
            for (String integration : toolsByIntegration.keySet()) {
                out.println("        if (userIntegrations.contains(\"" + integration + "\")) {");
                for (MCPToolDefinition tool : toolsByIntegration.get(integration)) {
                    out.println("            tools.add(create" + toCamelCase(tool.getName()) + "Tool());");
                }
                out.println("        }");
                out.println();
            }
            
            out.println("        return Map.of(\"tools\", tools);");
            out.println("    }");
            out.println();
            
            // Generate individual tool schema methods
            for (MCPToolDefinition tool : tools) {
                generateToolSchemaMethod(out, tool);
            }
            
            out.println("}");
        }
    }

    private void generateToolSchemaMethod(PrintWriter out, MCPToolDefinition tool) {
        String methodName = "create" + toCamelCase(tool.getName()) + "Tool";
        out.println("    private static Map<String, Object> " + methodName + "() {");
        out.println("        Map<String, Object> properties = new HashMap<>();");
        
        for (MCPParameterDefinition param : tool.getParameters()) {
            out.println("        properties.put(\"" + param.getName() + "\", Map.of(");
            out.println("            \"type\", \"" + param.getEffectiveType() + "\",");
            out.println("            \"description\", \"" + escapeJavaString(param.getDescription()) + "\"");
            if (!param.getExample().isEmpty()) {
                out.println("            , \"example\", \"" + escapeJavaString(param.getExample()) + "\"");
            }
            out.println("        ));");
        }
        
        // Build required parameters list
        out.println("        List<String> required = Arrays.asList(");
        boolean first = true;
        for (MCPParameterDefinition param : tool.getParameters()) {
            if (param.isRequired()) {
                if (!first) out.print(", ");
                out.print("\"" + param.getName() + "\"");
                first = false;
            }
        }
        out.println(");");
        
        out.println("        return Map.of(");
        out.println("            \"name\", \"" + tool.getName() + "\",");
        out.println("            \"description\", \"" + escapeJavaString(tool.getDescription()) + "\",");
        out.println("            \"inputSchema\", Map.of(");
        out.println("                \"type\", \"object\",");
        out.println("                \"properties\", properties,");
        out.println("                \"required\", required");
        out.println("            )");
        out.println("        );");
        out.println("    }");
        out.println();
    }

    private void generateParameterConversionMethod(PrintWriter out) {
        out.println("    @SuppressWarnings(\"unchecked\")");
        out.println("    private static <T> T convertParameter(Object value, String targetType, String paramName) {");
        out.println("        if (value == null) {");
        out.println("            return null;");
        out.println("        }");
        out.println();
        out.println("        // Handle JSONArray to String[] conversion");
        out.println("        if (targetType.equals(\"java.lang.String[]\") && value instanceof JSONArray) {");
        out.println("            JSONArray jsonArray = (JSONArray) value;");
        out.println("            String[] result = new String[jsonArray.length()];");
        out.println("            for (int i = 0; i < jsonArray.length(); i++) {");
        out.println("                result[i] = jsonArray.getString(i);");
        out.println("            }");
        out.println("            return (T) result;");
        out.println("        }");
        out.println();
        out.println("        // Handle JSONArray to Object[] conversion");
        out.println("        if (targetType.equals(\"java.lang.Object[]\") && value instanceof JSONArray) {");
        out.println("            JSONArray jsonArray = (JSONArray) value;");
        out.println("            Object[] result = new Object[jsonArray.length()];");
        out.println("            for (int i = 0; i < jsonArray.length(); i++) {");
        out.println("                result[i] = jsonArray.get(i);");
        out.println("            }");
        out.println("            return (T) result;");
        out.println("        }");
        out.println();
        out.println("        // Handle direct casting for other types");
        out.println("        try {");
        out.println("            return (T) value;");
        out.println("        } catch (ClassCastException e) {");
        out.println("            throw new IllegalArgumentException(\"Parameter '\" + paramName + \"' cannot be converted to \" + targetType + \". Value: \" + value + \", Type: \" + value.getClass().getSimpleName());");
        out.println("        }");
        out.println("    }");
        out.println();
    }

    private boolean isPrimitiveType(String javaType) {
        return javaType.equals("int") || 
               javaType.equals("long") || 
               javaType.equals("double") || 
               javaType.equals("float") || 
               javaType.equals("boolean") || 
               javaType.equals("char") || 
               javaType.equals("byte") || 
               javaType.equals("short");
    }
    
    private String toCamelCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : input.toCharArray()) {
            if (c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        
        return result.toString();
    }

    private String escapeJavaString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
} 