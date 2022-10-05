package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.service.ClassInfoService;
import ca.concordia.jaranalyzer.service.JarInfoService;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import org.eclipse.jdt.core.dom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Diptopol
 * @since 9/24/2021 4:25 PM
 */
public class TypeInferenceV2API {

    private static Logger logger = LoggerFactory.getLogger(TypeInferenceV2API.class);

    private static JarInfoService jarInfoService;
    private static ClassInfoService classInfoService;

    static {
        jarInfoService = new JarInfoService();
        classInfoService = new ClassInfoService();
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           MethodInvocation methodInvocation) {

        try {
            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(methodInvocation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            List<String> enclosingQualifiedClassNameList =
                    InferenceUtility.getAllEnclosingClassList(methodInvocation, dependentArtifactSet, javaVersion,
                            importStatementList, jarInfoService, classInfoService);
            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingQualifiedClassNameList, Collections.emptyList(), jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, methodInvocation));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            methodInvocation, jarInfoService, classInfoService);

            List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    methodInvocation, importStatementList, variableNameMap, owningClassInfo);

            return methodInfoList.isEmpty() ? null : methodInfoList.get(0);

        } catch (Exception | AssertionError e) {
            logger.error("Exception occurred", e);
        }

        return null;
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           SuperMethodInvocation superMethodInvocation) {

        try {
            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superMethodInvocation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            List<String> enclosingClassQNameList = InferenceUtility.getAllEnclosingClassList(superMethodInvocation,
                    dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService);
            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingClassQNameList, Collections.emptyList(), jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, superMethodInvocation));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            superMethodInvocation, jarInfoService, classInfoService);

            List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    superMethodInvocation, importStatementList, variableNameMap, owningClassInfo);

            return methodInfoList.isEmpty() ? null : methodInfoList.get(0);

        } catch (Exception | AssertionError e) {
            logger.error("Exception occurred", e);
        }

        return null;
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           ClassInstanceCreation classInstanceCreation) {

        try {
            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(classInstanceCreation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            List<String> enclosingClassQNameList =
                    InferenceUtility.getAllEnclosingClassList(classInstanceCreation, dependentArtifactSet, javaVersion,
                            importStatementList, jarInfoService, classInfoService);

            List<String> nonEnclosingAccessibleClassQNameList =
                    InferenceUtility.getNonEnclosingAccessibleClassListForInstantiation(classInstanceCreation, enclosingClassQNameList);

            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingClassQNameList, nonEnclosingAccessibleClassQNameList, jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, classInstanceCreation));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            classInstanceCreation, jarInfoService, classInfoService);

            List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    classInstanceCreation, importStatementList, variableNameMap, owningClassInfo);

            return methodInfoList.isEmpty() ? null : methodInfoList.get(0);

        } catch (Exception | AssertionError e) {
            logger.error("Exception occurred", e);
        }

        return null;
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           ConstructorInvocation constructorInvocation) {

        try {
            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(constructorInvocation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            List<String> enclosingClassQNameList = InferenceUtility.getAllEnclosingClassList(constructorInvocation,
                    dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService);
            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingClassQNameList, Collections.emptyList(), jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, constructorInvocation));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            constructorInvocation, jarInfoService, classInfoService);

            MethodDeclaration methodDeclaration =
                    (MethodDeclaration) InferenceUtility.getClosestASTNode(constructorInvocation, MethodDeclaration.class);

            String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
            String invokerClassName = className.replace("%", "").replace("$", ".");

            String methodName;

            if (className.contains("%.")) {
                methodName = className.substring(className.lastIndexOf("%.") + 2);
            } else if (className.contains(".")) {
                methodName = className.substring(className.lastIndexOf(".") + 1);
            } else {
                methodName = className;
            }

            methodName = methodName.replace("$", ".");

            List<Type> typeArgumentList = constructorInvocation.typeArguments();
            List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                    importStatementList, typeArgumentList, owningClassInfo);

            List<Expression> argumentList = constructorInvocation.arguments();
            int numberOfParameters = argumentList.size();

            List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                    javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo);

            TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                    .new Criteria(dependentArtifactSet, javaVersion,
                    importStatementList, methodName, numberOfParameters)
                    .setInvokerClassName(invokerClassName)
                    .setOwningClassInfo(owningClassInfo);

            for (int i = 0; i < argumentTypeInfoList.size(); i++) {
                searchCriteria.setArgumentTypeInfo(i, argumentTypeInfoList.get(i));
            }

            List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

            InferenceUtility.transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList,
                    owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList,
                    null, null, variableNameMap);
            InferenceUtility.conversionToVarargsMethodArgument(methodInfoList);

            return methodInfoList.isEmpty() ? null : methodInfoList.get(0);

        } catch (Exception | AssertionError e) {
            logger.error("Exception occurred", e);
        }

        return null;
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           SuperConstructorInvocation superConstructorInvocation) {

        try {
            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superConstructorInvocation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            List<String> enclosingClassQNameList = InferenceUtility.getAllEnclosingClassList(superConstructorInvocation,
                    dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService);
            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingClassQNameList, Collections.emptyList(), jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, superConstructorInvocation));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            superConstructorInvocation, jarInfoService, classInfoService);

            TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(superConstructorInvocation);
            Type superClassType = typeDeclaration.getSuperclassType();
            String superClassName;

            if (superClassType == null) {
                superClassName = "java.lang.Object";
            } else {
                TypeInfo superClassTypeInfo = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion,
                        importStatementList, superClassType, owningClassInfo);

                superClassName = Objects.nonNull(superClassTypeInfo)
                        ? superClassTypeInfo.getQualifiedClassName()
                        : null;
            }

            List<Type> typeArgumentList = superConstructorInvocation.typeArguments();
            List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                    importStatementList, typeArgumentList, owningClassInfo);

            List<Expression> argumentList = superConstructorInvocation.arguments();
            int numberOfParameters = argumentList.size();

            List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                    javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo);

            TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                    .new Criteria(dependentArtifactSet, javaVersion,
                    importStatementList, superClassName, numberOfParameters)
                    .setOwningClassInfo(owningClassInfo)
                    .setSuperInvoker(true);

            for (int i = 0; i < argumentTypeInfoList.size(); i++) {
                searchCriteria.setArgumentTypeInfo(i, argumentTypeInfoList.get(i));
            }

            List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

            InferenceUtility.transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList,
                    owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList,
                    null, null, variableNameMap);
            InferenceUtility.conversionToVarargsMethodArgument(methodInfoList);

            return methodInfoList.isEmpty() ? null : methodInfoList.get(0);

        } catch (Exception | AssertionError e) {
            logger.error("Exception occurred", e);
        }

        return null;
    }

}
