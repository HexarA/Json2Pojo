package net.hexar.json2pojo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.sun.codemodel.*;
import org.apache.commons.lang.StringUtils;
import org.jboss.dna.common.text.Inflector;

import javax.annotation.Generated;
import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * Contains the code to generate Java POJO classes from a given JSON text.
 */
class GeneratePojos {

    //region CONSTANTS -------------------------------------------------------------------------------------------------

    private static final boolean ALWAYS_ANNOTATE_EXPOSE = false;
    private static final boolean USE_M_PREFIX = true;

    //endregion

    //region ACTION CONTEXT --------------------------------------------------------------------------------------------

    private final VirtualFile mModuleSourceRoot;
    private final String mPackageName;
    private final ProgressIndicator mProgressBar;

    //endregion

    //region CLASS MAP -------------------------------------------------------------------------------------------------

    private Map<String, JDefinedClass> mClassMap = new HashMap<>();
    private JType mDeferredClass;
    private JType mDeferredList;
    private Map<JDefinedClass, Set<FieldInfo>> mFieldMap = new HashMap<>();

    //endregion

    //region CONSTRUCTOR -----------------------------------------------------------------------------------------------

    GeneratePojos(String packageName, VirtualFile moduleSourceRoot, ProgressIndicator progressBar) {
        mModuleSourceRoot = moduleSourceRoot;
        mPackageName = packageName;
        mProgressBar = progressBar;
    }

    //endregion

    //region CODE GENERATION -------------------------------------------------------------------------------------------

    /**
     * Generates POJOs from a source JSON text.
     *
     * @param rootName the name of the root class to generate.
     * @param json the source JSON text.
     * @param generateBuilders true if the generated class should omit setters and generate a builder instead.
     */
    void generateFromJson(String rootName, String json, boolean generateBuilders) {
        try {
            // Create code model and package
            JCodeModel jCodeModel = new JCodeModel();
            JPackage jPackage = jCodeModel._package(mPackageName);

            // Create deferrable types
            mDeferredClass = jCodeModel.ref(Deferred.class);
            mDeferredList = jCodeModel.ref(List.class).narrow(Deferred.class);

            // Parse the JSON data
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(json);

            // Create top-level class
            JDefinedClass rootClass = jPackage._class(rootName);
            annotateClass(rootClass);
            mClassMap.put(rootName, rootClass);
            mFieldMap.put(rootClass, new TreeSet<>(new FieldComparator()));

            // Recursively generate
            generate(rootNode, rootClass, jPackage, generateBuilders);

            // Build
            jCodeModel.build(new File(mModuleSourceRoot.getPath()));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.toString(), "Codegen Failed", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * Generates all of the sub-objects and fields for a given class.
     *
     * @param rootNode the JSON class node in the JSON syntax tree.
     * @param rootClass the class to generate sub-objects and fields for.
     * @param jPackage the code model package to generate the class in.
     * @param generateBuilders true if the generated class should omit setters and generate a builder instead.
     * @throws Exception if an error occurs.
     */
    private void generate(JsonNode rootNode, JDefinedClass rootClass, JPackage jPackage, boolean generateBuilders) throws Exception {
        // First create all referenced sub-types and collect field data
        parseTree(rootNode, rootClass, jPackage);

        // Now create the actual fields
        int i = 1;
        for (JDefinedClass clazz : mClassMap.values()) {
            // Generate the fields
            List<GeneratedField> fields = generateFields(clazz, mFieldMap.get(clazz), jPackage.owner(), generateBuilders);

            // Optionally generate the inner builder class
            if (generateBuilders) {
                generateBuilder(clazz, fields);
            }

            // Update progress
            mProgressBar.setFraction((double) i / (double) mClassMap.size());
            i++;
        }
    }

    /**
     * Generates all of the sub-objects for a given class.
     *
     * @param jPackage the code model package to generate the class in.
     * @param clazz the defined class to parse.
     * @param classNode the JSON class node in the JSON syntax tree.
     * @throws Exception if an error occurs.
     */
    private void parseTree(JsonNode classNode, JDefinedClass clazz, JPackage jPackage) throws Exception {
        // Iterate over all of the fields in this node
        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = classNode.fields();
        while (fieldsIterator.hasNext()) {
            // Get the field name and child node
            Map.Entry<String, JsonNode> entry = fieldsIterator.next();
            String propertyName = entry.getKey();
            JsonNode childNode = entry.getValue();

            // For arrays and objects, we need to create a new type
            if (childNode.isArray()) {
                // Determine the type of the first child
                Iterator<JsonNode> childNodesIterator = childNode.elements();
                while (childNodesIterator.hasNext()) {
                    JsonNode nextChild = childNodesIterator.next();

                    // Only create sub-types for objects
                    if (nextChild.isObject()) {
                        // Singularize the class name of a single element
                        String newClassName = formatClassName(Inflector.getInstance().singularize(propertyName));

                        // Find the class if it exists, or create it if it doesn't
                        JDefinedClass newClass;
                        if (mClassMap.containsKey(newClassName)) {
                            newClass = mClassMap.get(newClassName);
                        } else {
                            newClass = jPackage._class(newClassName);
                            annotateClass(newClass);
                            mClassMap.put(newClassName, newClass);
                            mFieldMap.put(newClass, new TreeSet<>(new FieldComparator()));
                        }

                        // Recursively generate its child objects and fields
                        parseTree(nextChild, newClass,jPackage);
                    }
                }
            } else if (childNode.isObject()) {
                // The class name should match the field name, except uppercase
                String newClassName = formatClassName(propertyName);

                // Find the class if it exists, or create it if it doesn't
                JDefinedClass newClass;
                if (mClassMap.containsKey(newClassName)) {
                    newClass = mClassMap.get(newClassName);
                } else {
                    newClass = jPackage._class(newClassName);
                    annotateClass(newClass);
                    mClassMap.put(newClassName, newClass);
                    mFieldMap.put(newClass, new TreeSet<>(new FieldComparator()));
                }

                // Recursively generate its child objects and fields
                parseTree(childNode, newClass, jPackage);
            }

            // Now attempt to create the field and add it to the field set
            FieldInfo field = getFieldInfoFromNode(childNode, propertyName, jPackage.owner());
            if (field != null) {
                mFieldMap.get(clazz).add(field);
            }
        }
    }

    /**
     * Creates a field in the given class.
     *
     * @param node the JSON node describing the field.
     * @param propertyName the name of the field to create.
     * @param jCodeModel the code model to use for generation.
     * @return a {@link FieldInfo} representing the new field.
     * @throws Exception if an error occurs.
     */
    private FieldInfo getFieldInfoFromNode(JsonNode node, String propertyName, JCodeModel jCodeModel) throws Exception {
        // Switch on node type
        if (node.isArray()) {
            // Singularize the class name of a single element
            String newClassName = formatClassName(Inflector.getInstance().singularize(propertyName));

            // Get the array type
            if (node.elements().hasNext()) {
                JsonNode firstNode = node.elements().next();
                if (firstNode.isObject()) {
                    // Get the already-created class from the class map
                    JDefinedClass newClass = mClassMap.get(newClassName);

                    // Now return the field referring to a list of the new class
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(newClass), propertyName);
                } else if (firstNode.isFloatingPointNumber()) {
                    // Now return the field referring to a list of doubles
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(Double.class), propertyName);
                } else if (firstNode.isIntegralNumber()) {
                    // Now return the field referring to a list of longs
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(Long.class), propertyName);
                } else if (firstNode.isNull()) {
                    // Null values? Return List<Deferred>.
                    return new FieldInfo(mDeferredList, propertyName);
                } else if (firstNode.isTextual()) {
                    // Now return the field referring to a list of strings
                    return new FieldInfo(jCodeModel.ref(List.class).narrow(String.class), propertyName);
                }
            } else {
                // No elements? Return List<Deferred>.
                return new FieldInfo(mDeferredList, propertyName);
            }
        } else if (node.isBoolean()) {
            return new FieldInfo(jCodeModel.ref(Boolean.class), propertyName);
        } else if (node.isFloatingPointNumber()) {
            return new FieldInfo(jCodeModel.ref(Double.class), propertyName);
        } else if (node.isIntegralNumber()) {
            return new FieldInfo(jCodeModel.ref(Long.class), propertyName);
        } else if (node.isNull()) {
            // Defer the type reference until later
            return new FieldInfo(mDeferredClass, propertyName);
        } else if (node.isObject()) {
            // Get the already-created class from the class map
            String newClassName = formatClassName(propertyName);
            JDefinedClass newClass = mClassMap.get(newClassName);

            // Now return the field as a defined class
            return new FieldInfo(newClass, propertyName);
        } else if (node.isTextual()) {
            return new FieldInfo(jCodeModel.ref(String.class), propertyName);
        }

        // If all else fails, return null
        return null;
    }

    /**
     * Generates all of the fields for a given class.
     *
     * @param clazz the class to generate sub-objects and fields for.
     * @param fields the set of fields to generate.
     * @param jCodeModel the code model.
     * @param generateBuilders true if the generated class should omit setters and generate a builder instead.
     * @return a list of generated fields.
     * @throws Exception if an error occurs.
     */
    private List<GeneratedField> generateFields(JDefinedClass clazz, Set<FieldInfo> fields, JCodeModel jCodeModel, boolean generateBuilders) throws Exception {
        List<GeneratedField> generatedFields = new ArrayList<>();

        // Get sorted list of field names
        for (FieldInfo fieldInfo : fields) {
            // Create field with correct naming scheme
            String fieldName = formatFieldName(fieldInfo.PropertyName);

            // Resolve deferred types
            JFieldVar newField;
            if (fieldInfo.Type.equals(mDeferredClass)) {
                // Attempt to get the class from the class map
                String newClassName = formatClassName(fieldInfo.PropertyName);
                JDefinedClass newClass = mClassMap.get(newClassName);

                // Now return the field for the actual class type
                if (newClass != null) {
                     newField = clazz.field(JMod.PRIVATE, newClass, fieldName);
                } else {
                    // Otherwise, just make a field of type Object
                    newField = clazz.field(JMod.PRIVATE, jCodeModel.ref(Object.class), fieldName);
                }
            } else if (fieldInfo.Type.equals(mDeferredList)) {
                // Attempt to get the class from the class map
                String newClassName = formatClassName(Inflector.getInstance().singularize(fieldInfo.PropertyName));
                JDefinedClass newClass = mClassMap.get(newClassName);

                // Now return the field referring to a list of the new class
                if (newClass != null) {
                    newField = clazz.field(JMod.PRIVATE, jCodeModel.ref(List.class).narrow(newClass), fieldName);
                } else {
                    // Otherwise, just make a field of type List<Object>
                    newField = clazz.field(JMod.PRIVATE, jCodeModel.ref(List.class).narrow(Object.class), fieldName);
                }
            } else {
                // The type should already be defined so just use it
                newField = clazz.field(JMod.PRIVATE, fieldInfo.Type, fieldName);
            }

            if (newField != null) {
                // Annotate field
                annotateField(newField, fieldInfo.PropertyName);

                // Create getter
                createGetter(clazz, newField, fieldInfo.PropertyName);

                // Create setter method only if we're not generating a builder class
                if (!generateBuilders) {
                    createSetter(clazz, newField, fieldInfo.PropertyName);
                }

                // Add field to return list
                generatedFields.add(new GeneratedField(newField, fieldInfo.PropertyName));
            }
        }

        return generatedFields;
    }

    /**
     * Generates the inner builder class for the containing class, with methods for the given fields.
     *
     * @param clazz the class to generate a builder class in.
     * @param fields the list of generated fields to build.
     * @throws Exception if an error occurs.
     */
    private void generateBuilder(JDefinedClass clazz, List<GeneratedField> fields) throws Exception {
        // Create the builder first
        JDefinedClass builder = clazz._class(JMod.PUBLIC | JMod.STATIC, "Builder");

        // Get sorted list of field names
        for (GeneratedField generatedField : fields) {
            // Create the new field
            builder.field(JMod.PRIVATE, generatedField.Field.type(), generatedField.Field.name());

            // Create the builder setter method
            createBuilderSetter(builder, generatedField.Field, generatedField.PropertyName);
        }

        // Create the build method
        createBuildMethod(clazz, builder, fields);
    }

    //endregion

    //region HELPER METHODS --------------------------------------------------------------------------------------------

    /**
     * Adds the {@link Generated} annotation to the class.
     *
     * @param clazz the class to annotate.
     */
    private static void annotateClass(JDefinedClass clazz) {
        clazz.annotate(Generated.class).param("value", "net.hexar.json2pojo");
        clazz.annotate(SuppressWarnings.class).param("value", "unused");
    }

    /**
     * Adds the {@link Expose} annotation and potentially the {@link SerializedName} annotation to a given
     * field - the latter is applied only if the property name differs from the field name.
     *
     * @param field the field to annotate.
     * @param propertyName the original JSON property name.
     */
    private static void annotateField(JFieldVar field, String propertyName) {
        // Use the SerializedName annotation if the field name doesn't match the property name
        if (!field.name().equals(propertyName)) {
            field.annotate(SerializedName.class).param("value", propertyName);

            // If we always add @Expose, then add this too
            if (ALWAYS_ANNOTATE_EXPOSE) {
                field.annotate(Expose.class);
            }
        } else {
            // Otherwise, just add @Expose
            field.annotate(Expose.class);
        }
    }

    /**
     * Generates a builder method for the given class, field, and property name.
     *
     * @param builder the class to generate a builder method in.
     * @param field the field to set.
     * @param propertyName the name of the property.
     * @return a {@link JMethod} which is a builder method for the given field.
     */
    private static JMethod createBuilderSetter(JDefinedClass builder, JFieldVar field, String propertyName) {
        // Method name should start with "set" and then the uppercased class name
        JMethod withMethod = builder.method(JMod.PUBLIC, builder, "with" + formatClassName(propertyName));

        // Set parameter name to lower camel case
        String paramName = sanitizePropertyName(propertyName);
        JVar param = withMethod.param(field.type(), paramName);

        // Assign to field name
        JBlock body = withMethod.body();
        if (field.name().equals(paramName)) {
            // Assign this.FieldName = paramName
            body.assign(JExpr._this().ref(field), param);
        } else {
            // Safe to just assign FieldName = paramName
            body.assign(field, param);
        }
        body._return(JExpr._this());
        return withMethod;
    }

    /**
     * Creates the build method for the builder.
     *
     * @param owner the containing class to build.
     * @param builder the builder to generate the build method for.
     * @param fields the list of generated fields that the containing class owns.
     */
    private JMethod createBuildMethod(JDefinedClass owner, JDefinedClass builder, List<GeneratedField> fields) {
        // Method name should start with "set" and then the uppercased class name
        JMethod buildMethod = builder.method(JMod.PUBLIC, owner, "build");

        // Assign to field name
        JBlock body = buildMethod.body();

        // Declare new instance of owner class
        String localName = sanitizePropertyName(owner.name());
        JVar local = body.decl(owner, localName, JExpr._new(owner));

        // Get sorted list of field names
        for (GeneratedField field : fields) {
            // Assign the field in the owner class
            body.assign(local.ref(field.Field.name()), JExpr.ref(field.Field.name()));
        }

        // Return the new instance
        body._return(local);
        return buildMethod;
    }

    /**
     * Generates a getter for the given class, field, and property name.
     *
     * @param clazz the class to generate a getter in.
     * @param field the field to return.
     * @param propertyName the name of the property.
     * @return a {@link JMethod} which is a getter for the given field.
     */
    private static JMethod createGetter(JDefinedClass clazz, JFieldVar field, String propertyName) {
        // Method name should start with "get" and then the uppercased class name
        JMethod getter = clazz.method(JMod.PUBLIC, field.type(), "get" + formatClassName(propertyName));

        // Return the field
        JBlock body = getter.body();
        body._return(field);
        return getter;
    }

    /**
     * Generates a setter for the given class, field, and property name.
     *
     * @param clazz the class to generate a setter in.
     * @param field the field to set.
     * @param propertyName the name of the property.
     * @return a {@link JMethod} which is a setter for the given field.
     */
    private static JMethod createSetter(JDefinedClass clazz, JFieldVar field, String propertyName) {
        // Method name should start with "set" and then the uppercased class name
        JMethod setter = clazz.method(JMod.PUBLIC, void.class, "set" + formatClassName(propertyName));

        // Set parameter name to lower camel case
        String paramName = sanitizePropertyName(propertyName);
        JVar param = setter.param(field.type(), paramName);

        // Assign to field name
        JBlock body = setter.body();
        if (field.name().equals(paramName)) {
            // Assign this.FieldName = paramName
            body.assign(JExpr._this().ref(field), param);
        } else {
            // Safe to just assign FieldName = paramName
            body.assign(field, param);
        }
        return setter;
    }

    /**
     * Formats the given property name into a more standard class name.
     *
     * @param propertyName the original property name.
     * @return the formatted class name.
     */
    static String formatClassName(String propertyName) {
        return StringUtils.capitalize(sanitizePropertyName(propertyName));
    }

    /**
     * Formats the given property name into a more standard field name.
     *
     * @param propertyName the original property name.
     * @return the formatted field name.
     */
    static String formatFieldName(String propertyName) {
        String fieldName = StringUtils.capitalize(sanitizePropertyName(propertyName));

        if (USE_M_PREFIX) {
            fieldName = "m" + fieldName;
        }
        return fieldName;
    }

    /**
     * Given a property name as a string, creates a valid identifier by removing non-alphanumeric characters and
     * uppercasing the letters after non-alphanumeric characters.
     *
     * @param propertyName the property name to format.
     * @return a String containing uppercased words, with underscores removed.
     */
    private static String sanitizePropertyName(String propertyName) {
        final StringBuilder formattedName = new StringBuilder();
        boolean uppercaseNext = false;

        // Avoid invalid starting characters for class / field names
        if (Character.isJavaIdentifierStart(propertyName.charAt(0))) {
            formattedName.append(propertyName.charAt(0));
        }

        // Iterate over the other characters
        for (int charIndex = 1; charIndex < propertyName.length(); charIndex++) {
            // Append valid characters
            Character c = propertyName.charAt(charIndex);
            if (Character.isAlphabetic(c)) {
                if (uppercaseNext) {
                    // Uppercase this letter
                    formattedName.append(Character.toUpperCase(c));
                    uppercaseNext = false;
                } else {
                    // Retain case
                    formattedName.append(propertyName.charAt(charIndex));
                }
            } else if (Character.isDigit(c)) {
                // Append as is
                formattedName.append(c);
            } else {
                // Don't append non-alphanumeric parts and uppercase next letter
                uppercaseNext = true;
            }
        }

        return formattedName.toString();
    }

    //endregion

    //region INNER CLASSES ---------------------------------------------------------------------------------------------

    /**
     * A class type that indicates that we don't yet know the type of data this field represents.
     */
    private static class Deferred {

    }

    /**
     * A comparator that sorts field data objects by field name, case insensitive.
     */
    private static class FieldComparator implements Comparator<FieldInfo> {
        @Override
        public int compare(FieldInfo left, FieldInfo right) {
            // Sort by formatted field name, not the property names
            return formatFieldName(left.PropertyName).compareTo(formatFieldName(right.PropertyName));
        }
    }

    /**
     * A simple representation of a field to be created.
     */
    private static class FieldInfo {
        final JType Type;
        final String PropertyName;

        FieldInfo(JType type, String propertyName) {
            Type = type;
            PropertyName = propertyName;
        }
    }

    /**
     * A pair containing a generated {@link JFieldVar} field and its original property name.
     */
    private static class GeneratedField {
        final JFieldVar Field;
        final String PropertyName;

        GeneratedField(JFieldVar field, String propertyName) {
            Field = field;
            PropertyName = propertyName;
        }
    }

    //endregion

}
