package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import io.vavr.Tuple3;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 4/22/2021 9:05 AM
 */
public abstract class TypeInferenceBase {

    private static final int MAX_SUPER_CLASS_DISTANCE = 1000;
    private static final int PRIMITIVE_TYPE_WIDENING_NARROWING_DISTANCE = 1;
    private static final int PRIMITIVE_TYPE_WRAPPING_DISTANCE = 1;

    private static Map<String, List<String>> PRIMITIVE_TYPE_WIDENING_MAP = new HashMap<>();
    private static Map<String, List<String>> PRIMITIVE_TYPE_NARROWING_MAP = new HashMap<>();

    private static Map<String, String> PRIMITIVE_WRAPPER_CLASS_MAP = new HashMap<>();
    private static Map<String, String> PRIMITIVE_UN_WRAPPER_CLASS_MAP = new HashMap<>();

    static {
        PRIMITIVE_WRAPPER_CLASS_MAP.put("boolean", "java.lang.Boolean");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("byte", "java.lang.Byte");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("char", "java.lang.Character");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("float", "java.lang.Float");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("int", "java.lang.Integer");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("long", "java.lang.Long");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("short", "java.lang.Short");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("double", "java.lang.Double");

        PRIMITIVE_WRAPPER_CLASS_MAP = Collections.unmodifiableMap(PRIMITIVE_WRAPPER_CLASS_MAP);

        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Boolean", "boolean");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put( "java.lang.Byte", "byte");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put( "java.lang.Character", "char");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Float", "float");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Integer", "int");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Long", "long");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Short", "short");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Double", "double");

        PRIMITIVE_UN_WRAPPER_CLASS_MAP = Collections.unmodifiableMap(PRIMITIVE_UN_WRAPPER_CLASS_MAP);

        PRIMITIVE_TYPE_WIDENING_MAP.put("byte", Arrays.asList("short", "int", "long", "float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("short", Arrays.asList("int", "long", "float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("char", Arrays.asList("int", "long", "float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("int", Arrays.asList("long", "float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("long", Arrays.asList("float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("float", Arrays.asList("double"));

        PRIMITIVE_TYPE_WIDENING_MAP = Collections.unmodifiableMap(PRIMITIVE_TYPE_WIDENING_MAP);

        PRIMITIVE_TYPE_NARROWING_MAP.put("short", Arrays.asList("byte", "char"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("char", Arrays.asList("byte", "short"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("int", Arrays.asList("byte", "short", "char"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("long", Arrays.asList("byte", "short", "char", "int"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("float", Arrays.asList("byte", "short", "char", "int", "long"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("double", Arrays.asList("byte", "short", "char", "int", "long", "float"));

        PRIMITIVE_TYPE_NARROWING_MAP = Collections.unmodifiableMap(PRIMITIVE_TYPE_NARROWING_MAP);
    }

    static List<MethodInfo> filterByMethodInvoker(List<MethodInfo> methodInfoList, String callerClassName,
                                                          boolean isSuperOfCallerClass, Object[] jarVertexIds,
                                                            TinkerGraph tinkerGraph) {
        if (!methodInfoList.isEmpty() && Objects.nonNull(callerClassName) && !callerClassName.equals("")) {
            Map<String, List<MethodInfo>> methodInfoDeclaringClassNameMap = new HashMap<>();

            String methodInfoClassName;
            for (MethodInfo methodInfo : methodInfoList) {
                methodInfoClassName = methodInfo.getQualifiedClassName();

                List<MethodInfo> methodInfoListForClass = methodInfoDeclaringClassNameMap.containsKey(methodInfoClassName)
                        ? methodInfoDeclaringClassNameMap.get(methodInfoClassName) : new ArrayList<>();

                methodInfoListForClass.add(methodInfo);
                methodInfoDeclaringClassNameMap.put(methodInfoClassName, methodInfoListForClass);
            }

            List<String> methodInfoClassNameList = new ArrayList<>(methodInfoDeclaringClassNameMap.keySet());

            List<MethodInfo> filteredListByCallerClassName = new ArrayList<>();

            if (methodInfoClassNameList.contains(callerClassName) && !isSuperOfCallerClass) {
                filteredListByCallerClassName.addAll(methodInfoDeclaringClassNameMap.get(callerClassName));

            } else if (callerClassName.contains("[]") && methodInfoClassNameList.contains("java.lang.Object")) {
                List<MethodInfo> qualifiedMethodInfoList = methodInfoDeclaringClassNameMap.get("java.lang.Object");
                qualifiedMethodInfoList.forEach(m -> m.setCallerClassMatchingDistance(MAX_SUPER_CLASS_DISTANCE));
                filteredListByCallerClassName.addAll(qualifiedMethodInfoList);

            } else {
                Set<String> classNameSet = new HashSet<>();
                classNameSet.add(callerClassName);

                String[] allOutGoingEdges = new String[]{"extends", "implements"};
                String[] superClassOutGoingEdgeLabels = isSuperOfCallerClass
                        ? new String[]{"extends"}
                        : allOutGoingEdges;

                Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

                int distance = 0;

                while (!classNameSet.isEmpty()) {
                    classNameSet = tinkerGraph.traversal().V(jarVertexIds)
                            .out("ContainsPkg").out("Contains")
                            .has("Kind", "Class")
                            .has("QName", TextP.within(classNameSet))
                            .out(superClassOutGoingEdgeLabels)
                            .<String>values("Name")
                            .toSet();

                    distance++;

                    superClassOutGoingEdgeLabels = allOutGoingEdges;

                    for (String className : methodInfoClassNameList) {
                        List<MethodInfo> qualifiedMethodInfoList = methodInfoDeclaringClassNameMap.get(className);

                        if (classNameSet.contains(className)) {
                            int finalDistance = className.equals("java.lang.Object") ? MAX_SUPER_CLASS_DISTANCE : distance;

                            qualifiedMethodInfoList.forEach(m->m.setCallerClassMatchingDistance(finalDistance));
                            filteredListByCallerClassName.addAll(qualifiedMethodInfoList);
                        }
                    }

                    if (!filteredListByCallerClassName.isEmpty()
                            && filteredListByCallerClassName.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                        deferredQualifiedMethodInfoSet.addAll(filteredListByCallerClassName);
                        filteredListByCallerClassName.clear();
                    }

                    if (!filteredListByCallerClassName.isEmpty()) {
                        break;
                    }
                }

                if (filteredListByCallerClassName.isEmpty() && !deferredQualifiedMethodInfoSet.isEmpty()) {
                    filteredListByCallerClassName.addAll(deferredQualifiedMethodInfoSet);
                }
            }

            if (filteredListByCallerClassName.size() > 1) {
                int minimumCallerClassMatchingDistance = getMinimumCallerClassMatchingDistance(filteredListByCallerClassName);

                filteredListByCallerClassName = filteredListByCallerClassName.stream()
                        .filter(m -> m.getCallerClassMatchingDistance() == minimumCallerClassMatchingDistance)
                        .collect(Collectors.toList());
            }

            return filteredListByCallerClassName;
        } else {
            return methodInfoList;
        }
    }

    static boolean matchMethodArguments(List<String> argumentTypeClassNameList,
                                        List<String> methodArgumentClassNameList,
                                        Object[] jarVertexIds,
                                        TinkerGraph tinkerGraph,
                                        MethodInfo methodInfo) {
        List<String> commonClassNameList = getCommonClassNameList(argumentTypeClassNameList, methodArgumentClassNameList);

        for (String commonClassName : commonClassNameList) {
            argumentTypeClassNameList.remove(commonClassName);
            methodArgumentClassNameList.remove(commonClassName);
        }

        if (argumentTypeClassNameList.isEmpty() && methodArgumentClassNameList.isEmpty()) {
            return true;
        }

        List<String> matchedMethodArgumentTypeList = new ArrayList<>();

        for (int index = 0; index < argumentTypeClassNameList.size(); index++) {
            String argumentTypeClassName = argumentTypeClassNameList.get(index);
            String methodArgumentTypeClassName = methodArgumentClassNameList.get(index);

            if (InferenceUtility.isPrimitiveType(argumentTypeClassName) && InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)) {
                if (isWideningPrimitiveConversion(argumentTypeClassName, methodArgumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance()
                            + PRIMITIVE_TYPE_WIDENING_NARROWING_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                } else if (isNarrowingPrimitiveConversion(argumentTypeClassName, methodArgumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance()
                            + PRIMITIVE_TYPE_WIDENING_NARROWING_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                } else {
                    return false;
                }
            }

            if (isNullType(argumentTypeClassName) && !InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)) {
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                continue;
            }

            // this check has to be done before `isArrayDimensionMismatch` checking
            if (methodArgumentTypeClassName.endsWith("[]") && methodInfo.isVarargs()
                    && isVarArgsMatch(methodArgumentTypeClassName,
                    argumentTypeClassNameList.subList(index, argumentTypeClassNameList.size()), jarVertexIds, tinkerGraph)) {

                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                break;
            }

            if (!methodArgumentTypeClassName.equals("java.lang.Object")
                    && matchObjectArrayDimensionForArgument(argumentTypeClassName, methodArgumentTypeClassName)) {
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                continue;
            }

            if (!methodArgumentTypeClassName.equals("java.lang.Object")
                    && isArrayDimensionMismatch(argumentTypeClassName, methodArgumentTypeClassName)) {
                return false;
            }

            if (InferenceUtility.isPrimitiveType(argumentTypeClassName)
                    && PRIMITIVE_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {

                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_WRAPPING_DISTANCE);
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
            }

            if (InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)
                    && PRIMITIVE_UN_WRAPPER_CLASS_MAP.containsKey(argumentTypeClassName)
                    && PRIMITIVE_UN_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {

                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_WRAPPING_DISTANCE);
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                continue;
            }

            /*
             * Trimmed down array dimension before searching for super classes.
             */
            argumentTypeClassName = argumentTypeClassName.replaceAll("\\[]", "");
            methodArgumentTypeClassName = methodArgumentTypeClassName.replaceAll("\\[]", "");
            methodArgumentClassNameList.set(index, methodArgumentTypeClassName);

            if (methodArgumentTypeClassName.contains("$")) {
                methodArgumentTypeClassName = methodArgumentTypeClassName.replace("$", ".");
                methodArgumentClassNameList.set(index, methodArgumentTypeClassName);

                if (methodArgumentTypeClassName.equals(argumentTypeClassName)) {
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                    continue;
                }
            }

            if (InferenceUtility.isPrimitiveType(argumentTypeClassName) && methodArgumentTypeClassName.equals("java.lang.Object")) {
                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + MAX_SUPER_CLASS_DISTANCE);
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                continue;
            }

            Set<String> classNameList = new HashSet<>();
            classNameList.add(argumentTypeClassName);

            int distance = 0;

            while (!classNameList.isEmpty()) {
                classNameList = getSuperClasses(classNameList, jarVertexIds, tinkerGraph);

                distance++;

                if (classNameList.contains(methodArgumentTypeClassName)) {
                    if (methodArgumentTypeClassName.equals("java.lang.Object")) {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + MAX_SUPER_CLASS_DISTANCE);
                    } else {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + distance);
                    }

                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                    break;
                }
            }
        }

        methodArgumentClassNameList.removeAll(matchedMethodArgumentTypeList);

        return methodArgumentClassNameList.isEmpty();
    }

    static List<MethodInfo> populateClassInfo(List<MethodInfo> qualifiedMethodInfoList, TinkerGraph tinkerGraph) {
        qualifiedMethodInfoList.forEach(m -> {
            Set<ClassInfo> classInfoSet = tinkerGraph.traversal()
                    .V(m.getId())
                    .in("Declares")
                    .toStream()
                    .map(ClassInfo::new)
                    .collect(Collectors.toSet());

            assert classInfoSet.size() == 1;

            m.setClassInfo(classInfoSet.iterator().next());
        });

        return qualifiedMethodInfoList;
    }


    static List<ClassInfo> resolveQClassInfoForClass(String typeClassName,
                                                 Object[] jarVertexIds,
                                                 Set<String> importedClassQNameList,
                                                 List<String> packageNameList,
                                                 TinkerGraph tinkerGraph) {

        if (Objects.nonNull(typeClassName) && !InferenceUtility.isPrimitiveType(typeClassName)
                && StringUtils.countMatches(typeClassName, ".") <= 1) {

            String postProcessedTypeClassName = typeClassName.replace(".", "$")
                    .replaceAll("\\[]", "");

            List<ClassInfo> qualifiedClassInfoList = tinkerGraph.traversal().V(jarVertexIds)
                    .out("ContainsPkg").out("Contains")
                    .has("Kind", "Class")
                    .has("Name", TextP.containing(postProcessedTypeClassName))
                    .toStream()
                    .map(ClassInfo::new)
                    .collect(Collectors.toList());

            qualifiedClassInfoList = qualifiedClassInfoList.stream().filter(classInfo -> {
                if (classInfo.isInnerClass()) {
                    if (classInfo.isPrivate()) {
                        return false;
                    }

                    boolean classNameCheck;
                    if (postProcessedTypeClassName.contains("$")) {
                        classNameCheck = classInfo.getName().equals(postProcessedTypeClassName);
                    } else {
                        classNameCheck = classInfo.getName().endsWith("$" + postProcessedTypeClassName);
                    }

                    if (classNameCheck) {
                        String qualifiedClassName = classInfo.getQualifiedName();
                        String qualifiedOuterClassName = classInfo.getQualifiedName()
                                .substring(0, classInfo.getQualifiedName().lastIndexOf("."));

                        return importedClassQNameList.contains(qualifiedClassName)
                                || importedClassQNameList.contains(qualifiedOuterClassName)
                                || packageNameList.contains(classInfo.getPackageName());

                    } else {
                        return false;
                    }
                } else {
                    return classInfo.getName().equals(postProcessedTypeClassName)
                            && (importedClassQNameList.contains(classInfo.getQualifiedName())
                            || packageNameList.contains(classInfo.getPackageName()));
                }
            }).collect(Collectors.toList());

            return qualifiedClassInfoList;
        }

        return Collections.emptyList();
    }

    static String resolveQNameForClass(String typeClassName,
                                       Object[] jarVertexIds,
                                       Set<String> importedClassQNameSet,
                                       List<String> packageNameList,
                                       TinkerGraph tinkerGraph) {
        int numberOfArrayDimensions = StringUtils.countMatches(typeClassName, "[]");

        List<ClassInfo> qualifiedClassInfoList = resolveQClassInfoForClass(typeClassName, jarVertexIds,
                importedClassQNameSet, packageNameList, tinkerGraph);

        qualifiedClassInfoList = filtrationBasedOnPrioritization(typeClassName, importedClassQNameSet, qualifiedClassInfoList);

        return qualifiedClassInfoList.isEmpty()
                ? typeClassName
                : getQualifiedNameWithArrayDimension(qualifiedClassInfoList.get(0).getQualifiedName(), numberOfArrayDimensions);
    }

    static List<ClassInfo> filtrationBasedOnPrioritization(String typeClassName,
                                                           Set<String> importedClassQNameSet,
                                                           List<ClassInfo> qualifiedClassInfoList) {
        /*
         * If there are multiple result, we want to give priority for classes who are directly mentioned in import
         * statement or belongs to 'java.lang' package. Because * package import can have many classes which satisfies
         * the same condition. But we will only want to consider * package import if there is no directly mentioned class.
         *
         * For inner classes, we will be only able to find type name as inner class name if import contains name of inner class
         * otherwise we will find outer class suffix during type declaration.
         *
         * Hierarchy must be maintained. First we have to check type direct import and then java.lang package.
         */
        Predicate<ClassInfo> isClassNameDirectImport = c -> (StringUtils.countMatches(typeClassName, ".") == 1
                ? importedClassQNameSet.contains(c.getQualifiedName().substring(0, c.getQualifiedName().lastIndexOf(".")))
                : importedClassQNameSet.contains(c.getQualifiedName()));

        if (qualifiedClassInfoList.size() > 1 && qualifiedClassInfoList.stream().anyMatch(isClassNameDirectImport)) {
            qualifiedClassInfoList = qualifiedClassInfoList.stream()
                    .filter(isClassNameDirectImport)
                    .collect(Collectors.toList());
        }

        if (qualifiedClassInfoList.size() > 1 && qualifiedClassInfoList.stream().anyMatch(c -> c.getPackageName().equals("java.lang"))) {
            qualifiedClassInfoList = qualifiedClassInfoList.stream()
                    .filter(c -> c.getPackageName().equals("java.lang"))
                    .collect(Collectors.toList());
        }

        return qualifiedClassInfoList;
    }

    static List<MethodInfo> getQualifiedMethodInfoList(String methodName, int numberOfParameters,
                                                        Object[] jarVertexIds, Set<String> classQNameList, TinkerGraph tinkerGraph) {

        String outerClassPrefix = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(0, methodName.indexOf("."))
                : null;

        methodName = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(methodName.indexOf(".") + 1)
                : methodName;

        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> {
                    boolean innerClassConstructorMatching = outerClassPrefix == null
                            || methodInfo.getInternalClassConstructorPrefix().equals(outerClassPrefix + "$");

                    return innerClassConstructorMatching
                            && (methodInfo.getArgumentTypes().length == numberOfParameters || methodInfo.isVarargs());
                })
                .collect(Collectors.toList());
    }

    static List<MethodInfo> getQualifiedMethodInfoListForInnerClass(String methodName, int numberOfParameters,
                                                                              Object[] jarVertexIds, Set<String> classQNameList, TinkerGraph tinkerGraph) {

        String outerClassPrefix = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(0, methodName.indexOf("."))
                : null;

        methodName = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(methodName.indexOf(".") + 1)
                : methodName;

        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
                .out("ContainsInnerClass")
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> {
                    boolean innerClassConstructorMatching = outerClassPrefix == null
                            || methodInfo.getInternalClassConstructorPrefix().equals(outerClassPrefix + "$");

                    return innerClassConstructorMatching
                            && (methodInfo.getArgumentTypes().length == numberOfParameters || methodInfo.isVarargs());
                })
                .collect(Collectors.toList());
    }


    static List<MethodInfo> getQualifiedMethodInfoListForPackageImport(String methodName,
                                                                        int numberOfParameters,
                                                                        List<String> packageNameList,
                                                                        Set<String> importedClassQNameSet,
                                                                        Object[] jarVertexIds,
                                                                        TinkerGraph tinkerGraph) {
        Set<String> classNameListForPackgage = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg")
                .has("Kind", "Package")
                .has("Name", TextP.within(packageNameList))
                .out("Contains")
                .has("Kind", "Class")
                .<String>values("QName")
                .toSet();

        importedClassQNameSet.addAll(classNameListForPackgage);

        return getQualifiedMethodInfoList(methodName, numberOfParameters, jarVertexIds, classNameListForPackgage, tinkerGraph);
    }


    static Set<String> getSuperClasses(Set<String> classQNameList,
                                                 Object[] jarVertexIds,
                                                 TinkerGraph tinkerGraph) {
        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
                .out("extends", "implements")
                .<String>values("Name")
                .toSet();
    }

    static Object[] getJarVertexIds(Set<Tuple3<String, String, String>> jarInformationSet,
                                              String javaVersion, TinkerGraph tinkerGraph) {
        Set<Object> jarVertexIdSet = new HashSet<>();

        jarInformationSet.forEach(j -> {
            jarVertexIdSet.addAll(
                    tinkerGraph.traversal().V()
                            .has("Kind", "Jar")
                            .has("GroupId", j._1)
                            .has("ArtifactId", j._2)
                            .has("Version", j._3)
                            .toStream()
                            .map(Element::id)
                            .collect(Collectors.toSet())
            );
        });

        jarVertexIdSet.addAll(
                tinkerGraph.traversal().V()
                        .has("Kind", "Jar")
                        .has("ArtifactId", "Java")
                        .has("Version", javaVersion)
                        .toStream()
                        .map(Element::id)
                        .collect(Collectors.toSet())
        );

        return jarVertexIdSet.toArray(new Object[0]);
    }


    static List<String> getPackageNameList(List<String> importList) {
        List<String> nonImportStaticList = importList.stream().filter(im -> !im.startsWith("import static")).collect(Collectors.toList());

        return nonImportStaticList.stream()
                .filter(im -> im.endsWith(".*"))
                .map(im -> im.substring(0, im.lastIndexOf(".*")).replace("import", "").trim())
                .collect(Collectors.toList());
    }

    static Set<String> getImportedQNameList(List<String> importList) {
        Set<String> importedClassQNameList = new HashSet<>();
        List<String> importStaticList = importList.stream().filter(im -> im.startsWith("import static")).collect(Collectors.toList());
        List<String> nonImportStaticList = importList.stream().filter(im -> !im.startsWith("import static")).collect(Collectors.toList());

        importedClassQNameList.addAll(
                nonImportStaticList.stream()
                        .filter(im -> !im.endsWith(".*"))
                        .map(im -> im.replace("import", "").trim())
                        .collect(Collectors.toSet())
        );

        importedClassQNameList.addAll(
                importStaticList.stream()
                        .map(im -> im.substring(0, im.lastIndexOf(".")).replace("import static", "").trim())
                        .collect(Collectors.toSet())
        );

        return importedClassQNameList;
    }


    static String processMethodName(String methodName, Set<String> importedClassQNameSet) {
        /*
          Method name may contains parameterized type (e.g ArrayList<String>). So removal of parameterized type is required
          before method name matching.
         */
        if (methodName.contains("<") && methodName.contains(">")) {
            int startIndex = methodName.indexOf("<");
            int endIndex = methodName.lastIndexOf(">") + 1;

            methodName = methodName.replace(methodName.substring(startIndex, endIndex), "");
        }

        /*
          For fully qualified method expression, We are extracting fully qualified class name as import and method name
         */
        if (methodName.contains(".")) {
            importedClassQNameSet.add(methodName);

            if (StringUtils.countMatches(methodName, ".") > 1) {
                methodName = methodName.substring(methodName.lastIndexOf(".") + 1);
            }
        }

        return methodName;
    }

    static int getMinimumCallerClassMatchingDistance(Collection<MethodInfo> methodInfoCollection) {
        return Optional.of(methodInfoCollection)
                .orElse(Collections.emptyList())
                .stream()
                .map(MethodInfo::getCallerClassMatchingDistance)
                .mapToInt(v -> v)
                .min()
                .orElse(0);
    }

    static int getMinimumArgumentMatchingDistance(Collection<MethodInfo> methodInfoCollection) {
        return Optional.of(methodInfoCollection)
                .orElse(Collections.emptyList())
                .stream()
                .map(MethodInfo::getArgumentMatchingDistance)
                .mapToInt(v -> v)
                .min()
                .orElse(0);
    }

    static void modifyMethodInfoForArray(List<MethodInfo> methodInfoList, String callerClassName) {
        if (callerClassName != null && callerClassName.endsWith("[]")) {
            int dimension = StringUtils.countMatches(callerClassName, "[]");
            String typeName = callerClassName.replaceAll("\\[]", "");

            typeName = InferenceUtility.isPrimitiveType(typeName)
                    ? getTypeDescriptorForPrimitive(typeName)
                    : "L" + typeName.replaceAll("\\.", "/") + ";";

            Type returnType = Type.getType(StringUtils.repeat("[", dimension) + typeName);

            methodInfoList.forEach(m -> {
                m.setReturnType(returnType);
                m.setThrownInternalClassNames(Collections.emptyList());
            });
        }
    }

    static String getTypeDescriptorForPrimitive(String type) {
        if (!InferenceUtility.isPrimitiveType(type)) {
            return null;
        }

        switch (type) {
            case "boolean":
                return "Z";
            case "char":
                return "C";
            case "byte":
                return "B";
            case "short":
                return "S";
            case "int":
                return "I";
            case "float":
                return "F";
            case "long":
                return "J";
            case "double":
                return "D";
            default:
                throw new IllegalStateException();
        }
    }

    static Set<MethodInfo> filteredNonAbstractMethod(Set<MethodInfo> methodInfoSet) {
        return new HashSet<>(filteredNonAbstractMethod(new ArrayList<>(methodInfoSet)));
    }

    static List<MethodInfo> filteredNonAbstractMethod(List<MethodInfo> methodInfoList) {
        if (methodInfoList.size() > 1 && methodInfoList.stream().anyMatch(m -> !m.isAbstract())) {
            return methodInfoList.stream().filter(m -> !m.isAbstract()).collect(Collectors.toList());
        } else {
            return methodInfoList;
        }
    }

    private static String getQualifiedNameWithArrayDimension(String qualifiedClassName, int arrayDimension) {
        StringBuilder qualifiedClassNameBuilder = new StringBuilder(qualifiedClassName);

        for (int i = 0; i < arrayDimension; i++) {
            qualifiedClassNameBuilder.append("[]");
        }

        return qualifiedClassNameBuilder.toString();
    }

    private static boolean isNullType(String name) {
        return "null".equals(name);
    }

    private static boolean isWideningPrimitiveConversion(String type1, String type2) {
        return PRIMITIVE_TYPE_WIDENING_MAP.containsKey(type1) && PRIMITIVE_TYPE_WIDENING_MAP.get(type1).contains(type2);
    }

    private static boolean isNarrowingPrimitiveConversion(String type1, String type2) {
        return PRIMITIVE_TYPE_NARROWING_MAP.containsKey(type1) && PRIMITIVE_TYPE_NARROWING_MAP.get(type1).contains(type2);
    }

    private static List<String> getCommonClassNameList(List<String> argumentTypeClassNameList,
                                                       List<String> methodArgumentClassNameList) {

        int size = Math.min(argumentTypeClassNameList.size(), methodArgumentClassNameList.size());
        List<String> commonClassNameList = new ArrayList<>();

        for (int index = 0; index < size; index++) {
            if (argumentTypeClassNameList.get(index).equals(methodArgumentClassNameList.get(index))) {
                commonClassNameList.add(argumentTypeClassNameList.get(index));
            }
        }

        return commonClassNameList;
    }

    private static boolean matchObjectArrayDimensionForArgument(String argumentTypeClassName,
                                                                String methodArgumentTypeClassName) {

        boolean isArgumentTypeArray = argumentTypeClassName.endsWith("[]");
        int argumentTypeArrayDimension = StringUtils.countMatches(argumentTypeClassName, "[]");

        boolean isMethodArgumentTypeArray = methodArgumentTypeClassName.endsWith("[]");
        int methodArgumentTypeArrayDimension = StringUtils.countMatches(methodArgumentTypeClassName, "[]");

        return isMethodArgumentTypeArray && isArgumentTypeArray
                && methodArgumentTypeClassName.startsWith("java.lang.Object")
                && methodArgumentTypeArrayDimension + 1 == argumentTypeArrayDimension;
    }

    private static boolean isArrayDimensionMismatch(String argumentTypeClassName, String methodArgumentTypeClassName) {
        boolean isArgumentTypeArray = argumentTypeClassName.endsWith("[]");
        int argumentTypeArrayDimension = StringUtils.countMatches(argumentTypeClassName, "[]");

        boolean isMethodArgumentTypeArray = methodArgumentTypeClassName.endsWith("[]");
        int methodArgumentTypeArrayDimension = StringUtils.countMatches(methodArgumentTypeClassName, "[]");

        return (isArgumentTypeArray && !isMethodArgumentTypeArray)
                || (!isArgumentTypeArray && isMethodArgumentTypeArray)
                || argumentTypeArrayDimension != methodArgumentTypeArrayDimension;
    }

    private static boolean isVarArgsMatch(String methodArgumentTypeClassName,
                                          List<String> varArgsTypeClassNameList,
                                          Object[] jarVertexIds,
                                          TinkerGraph tinkerGraph) {
        String typeClassName = methodArgumentTypeClassName.replaceAll("\\[]$", "");

        if (varArgsTypeClassNameList.stream().anyMatch(name -> isArrayDimensionMismatch(name, typeClassName))) {
            return false;
        }

        if (varArgsTypeClassNameList.stream().allMatch(name -> name.equals(typeClassName))) {
            return true;
        }

        String methodArgumentTypeName = typeClassName.replaceAll("\\[]", "");

        varArgsTypeClassNameList = varArgsTypeClassNameList.stream().distinct().collect(Collectors.toList());

        return varArgsTypeClassNameList.stream().allMatch(varArgTypeName -> {
            varArgTypeName = varArgTypeName.replaceAll("\\[]", "");

            if (InferenceUtility.isPrimitiveType(varArgTypeName)) {
                varArgTypeName = PRIMITIVE_WRAPPER_CLASS_MAP.get(varArgTypeName);
            }

            Set<String> classNameSet = Collections.singleton(varArgTypeName);
            boolean matched = false;

            while (!classNameSet.isEmpty()) {
                classNameSet = getSuperClasses(classNameSet, jarVertexIds, tinkerGraph);

                if (classNameSet.contains(methodArgumentTypeName)) {
                    matched = true;
                    break;
                }
            }

            return matched;
        });
    }

}
