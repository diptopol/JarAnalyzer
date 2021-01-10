package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple3;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;

/**
 * @author Diptopol
 * @since 12/23/2020 9:01 PM
 */
public class TypeInferenceAPI {

    private static Logger logger = LoggerFactory.getLogger(TypeInferenceAPI.class);

    private static TinkerGraph tinkerGraph;
    private static JarAnalyzer jarAnalyzer;

    static {
        Configuration configuration = new BaseConfiguration();
        configuration.addProperty("gremlin.tinkergraph.defaultVertexPropertyCardinality", "list");

        tinkerGraph = TinkerGraph.open(configuration);
        jarAnalyzer = new JarAnalyzer(tinkerGraph);

        if (!Files.exists(getJarStoragePath())) {
            createClassStructureGraphForJavaJars();
            storeClassStructureGraph();
        } else {
            loadClassStructureGraph();
        }
    }

    public static Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName, Repository repository) {
        Set<Tuple3<String, String, String>> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom(commitId, projectName, repository);

        Set<Tuple3<String, String, String>> jarArtifactInfoSetForLoad = jarArtifactInfoSet.stream()
                .filter(jarArtifactInfo -> !isJarExists(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3))
                .collect(Collectors.toSet());

        jarArtifactInfoSetForLoad.forEach(jarArtifactInfo -> {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3);

            jarAnalyzer.toGraph(jarInformation);
        });

        if (jarArtifactInfoSetForLoad.size() > 0) {
            storeClassStructureGraph();
        }

        return jarArtifactInfoSet;
    }

    /**
     * The process of checking classes for specific method will happen in three steps.<br><br>
     *
     * <strong>Step 1</strong>: All the classes who are directly mentioned in the import statement will be checked,
     * if method found it will be returned.<br>
     *
     * <strong>Step 2</strong>: All the classes under on-demand package import will be searched, if method found
     * it will be returned.<br>
     *
     * <strong>Step 3</strong>: Recursively look for super classes and interfaces from all the import classes (on demand and normal)
     * if in any step method is found it will be returned, otherwise recursion will happen until java.lang.Object is
     * reached, then if no method is found an empty list will be returned.<br>
     */
    public static List<MethodInfo> getAllMethods(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                 String javaVersion,
                                                 List<String> importList,
                                                 String methodName,
                                                 int numberOfParameters) {

        Object[] jarVertexIds = getJarVertexIds(dependentJarInformationSet, javaVersion);

        List<String> importStaticList = importList.stream().filter(im -> im.startsWith("import static")).collect(Collectors.toList());
        List<String> nonImportStaticList = importList.stream().filter(im -> !im.startsWith("import static")).collect(Collectors.toList());
        Set<String> importedClassQNameList = new HashSet<>();

        /*
          STEP 1
         */
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

        /*
          Method name may contains parameterized type (e.g ArrayList<String>). So removal of parameterized type is required
          before method name matching.
         */
        if (methodName.contains("<") && methodName.contains(">")) {
            int startIndex = methodName.lastIndexOf("<");
            int endIndex = methodName.lastIndexOf(">") + 1;

            methodName = methodName.replace(methodName.substring(startIndex, endIndex), "");
        }

        /*
          For fully qualified method expression, We are extracting fully qualified class name as import and method name
         */
        if (methodName.contains(".")) {
            importedClassQNameList.add(methodName);
            methodName = methodName.substring(methodName.lastIndexOf(".") + 1);
        }

        Set<String> innerClassSet = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(importedClassQNameList))
                .out("Declares")
                .has("Kind", "InnerClass")
                .<String>values("Name")
                .toSet();

        importedClassQNameList.addAll(innerClassSet);

        List<MethodInfo> qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                jarVertexIds, importedClassQNameList);

        if (!qualifiedMethodInfoList.isEmpty()) {
            return populateClassInfo(qualifiedMethodInfoList);
        }

        /*
          STEP 2
         */
        List<String> packageNameList = nonImportStaticList.stream()
                .filter(im -> im.endsWith(".*"))
                .map(im -> im.substring(0, im.lastIndexOf(".*")).replace("import", "").trim())
                .collect(Collectors.toList());

        Set<String> classNameListForPackgage = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg")
                .has("Kind", "Package")
                .has("Name", TextP.within(packageNameList))
                .out("Contains")
                .has("Kind", "Class")
                .<String>values("QName")
                .toSet();

        importedClassQNameList.addAll(classNameListForPackgage);

        qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters, jarVertexIds, classNameListForPackgage);

        if (!qualifiedMethodInfoList.isEmpty()) {
            return populateClassInfo(qualifiedMethodInfoList);
        }

        /*
          STEP 3
         */
        Set<String> classQNameList = new HashSet<>(importedClassQNameList);

        while (!classQNameList.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
            classQNameList = tinkerGraph.traversal().V(jarVertexIds)
                    .out("ContainsPkg").out("Contains")
                    .has("Kind", "Class")
                    .has("QName", TextP.within(classQNameList))
                    .out("extends", "implements")
                    .<String>values("Name")
                    .toSet();

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters, jarVertexIds, classQNameList);
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return populateClassInfo(qualifiedMethodInfoList);

        } else {
            return Collections.emptyList();
        }
    }

    private static List<MethodInfo> populateClassInfo(List<MethodInfo> qualifiedMethodInfoList) {
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

    private static List<MethodInfo> getQualifiedMethodInfoList(String methodName, int numberOfParameters,
                                                               Object[] jarVertexIds, Set<String> classQNameList) {

        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> methodInfo.getArgumentTypes().length == numberOfParameters)
                .collect(Collectors.toList());
    }

    private static Object[] getJarVertexIds(Set<Tuple3<String, String, String>> jarInformationSet, String javaVersion) {
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

    public static List<String> getQualifiedClassName(String className) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Class")
                .has("Name", className)
                .<String>values("QName")
                .toList();
    }


    public static void loadJar(String groupId, String artifactId, String version) {
        if (!isJarExists(groupId, artifactId, version)) {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(groupId, artifactId, version);

            jarAnalyzer.toGraph(jarInformation);
            storeClassStructureGraph();
        }
    }

    private static boolean isJarExists(String groupId, String artifactId, String version) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Jar")
                .has("GroupId", groupId)
                .has("ArtifactId", artifactId)
                .has("Version", version)
                .toSet().size() > 0;
    }

    private static void createClassStructureGraphForJavaJars() {
        String javaJarDirectory = getProperty("java.jar.directory");
        String javaVersion = getProperty("java.version");

        logger.info("Java Jar Directory: {}", javaJarDirectory);
        logger.info("Java Version: {}", javaVersion);

        if (javaJarDirectory != null) {
            List<String> jarFiles = Utility.getFiles(javaJarDirectory, "jar");
            for (String jarLocation : jarFiles) {
                try {
                    Path path = Paths.get(jarLocation);
                    if (Files.exists(path)) {
                        JarFile jarFile = new JarFile(new File(jarLocation));
                        jarAnalyzer.jarToGraph(jarFile, path.getFileName().toString(), "Java", javaVersion);
                    }
                } catch (Exception e) {
                    logger.error("Could not open the JAR", e);
                }
            }

        }
    }

    private static void storeClassStructureGraph() {
        logger.info("storing graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.writer, IO.gryo)
                .write().iterate();
    }

    private static void loadClassStructureGraph() {
        logger.info("loading graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.reader, IO.gryo)
                .read().iterate();
    }
}
