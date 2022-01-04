package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.Models.TypeObject;
import ca.concordia.jaranalyzer.Models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import io.vavr.Tuple3;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * @author Diptopol
 * @since 9/24/2021 4:25 PM
 */
public class TypeInferenceV2API {

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           MethodInvocation methodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(methodInvocation);
        String owningClassQualifiedName = getOwingClassQualifiedName(methodInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        addSpecialImportStatements(importStatementList, compilationUnit, methodInvocation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList,
                        methodInvocation, owningClassQualifiedName);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                methodInvocation, importStatementList, variableNameMap, owningClassQualifiedName);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           SuperMethodInvocation superMethodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superMethodInvocation);
        String owningClassQualifiedName = getOwingClassQualifiedName(superMethodInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        addSpecialImportStatements(importStatementList, compilationUnit, superMethodInvocation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList,
                        superMethodInvocation, owningClassQualifiedName);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                superMethodInvocation, importStatementList, variableNameMap, owningClassQualifiedName);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           ClassInstanceCreation classInstanceCreation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(classInstanceCreation);
        String owingClassQualifiedName = getOwingClassQualifiedName(classInstanceCreation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        addSpecialImportStatements(importStatementList, compilationUnit, classInstanceCreation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList,
                        classInstanceCreation, owingClassQualifiedName);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                classInstanceCreation, importStatementList, variableNameMap, owingClassQualifiedName);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                    String javaVersion,
                                    ConstructorInvocation constructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(constructorInvocation);
        String owingClassQualifiedName = getOwingClassQualifiedName(constructorInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        addSpecialImportStatements(importStatementList, compilationUnit, constructorInvocation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList,
                        constructorInvocation, owingClassQualifiedName);

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(constructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        String methodName;

        if (className.contains("%.")) {
            methodName = className.substring(className.lastIndexOf("%.") + 2);
        } else if (className.contains(".")) {
            methodName = className.substring(className.lastIndexOf(".") + 1);
        } else {
            methodName = className;
        }

        methodName = methodName.replace("#", ".");

        List<Expression> argumentList = constructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<TypeObject> argumentTypeObjList = InferenceUtility.getArgumentTypeObjList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owingClassQualifiedName);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningClassQualifiedName(owingClassQualifiedName);

        for (int i = 0; i < argumentTypeObjList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeObjList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                    String javaVersion,
                                    SuperConstructorInvocation superConstructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superConstructorInvocation);
        String owingClassQualifiedName = getOwingClassQualifiedName(superConstructorInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        addSpecialImportStatements(importStatementList, compilationUnit, superConstructorInvocation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion,
                        importStatementList, superConstructorInvocation, owingClassQualifiedName);

        TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(superConstructorInvocation);
        Type superClassType = typeDeclaration.getSuperclassType();
        String superClassName;

        if (superClassType == null) {
            superClassName = "java.lang.Object";
        } else {
            superClassName = InferenceUtility.getTypeObj(dependentJarInformationSet, javaVersion,
                    importStatementList, superClassType, owingClassQualifiedName).getQualifiedClassName();
        }

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(superConstructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        List<Expression> argumentList = superConstructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<TypeObject> argumentTypeObjList = InferenceUtility.getArgumentTypeObjList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owingClassQualifiedName);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, superClassName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningClassQualifiedName(owingClassQualifiedName)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentTypeObjList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeObjList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    private static void addSpecialImportStatements(List<String> importStatementList,
                                                                CompilationUnit compilationUnit,
                                                                ASTNode methodNode) {
        // all java classes can access methods and classes of java.lang package without import statement
        importStatementList.add("import java.lang.*");

        // all classes under the current package can be accessed without import statement
        PackageDeclaration packageDeclaration = compilationUnit.getPackage();
        importStatementList.add("import " + packageDeclaration.getName().getFullyQualifiedName() + ".*");

        // added inner classes of the current file in the import statement
        TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(methodNode);
        importStatementList.add("import " + InferenceUtility.getQualifiedClassName(typeDeclaration));
        TypeDeclaration[] innerTypeDeclarationArray = typeDeclaration.getTypes();

        for (TypeDeclaration innerClassDeclaration : innerTypeDeclarationArray) {
            importStatementList.add("import " +
                    InferenceUtility.getQualifiedClassName(innerClassDeclaration).replaceAll("#", "."));
        }
    }

    private static  String getOwingClassQualifiedName(ASTNode methodNode) {
        TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(methodNode);

        return InferenceUtility.getQualifiedClassName(typeDeclaration);
    }

}
