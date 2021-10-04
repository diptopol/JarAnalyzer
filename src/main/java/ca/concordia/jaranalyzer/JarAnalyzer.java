package ca.concordia.jaranalyzer;


import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.PackageInfo;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.lib.Repository;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.ofNullable;


public class JarAnalyzer {

    private static Logger logger = LoggerFactory.getLogger(JarAnalyzer.class);

    /*
     * After removal of APIFinderImpl, this instance will be made private
     */
    public TinkerGraph graph;

    public JarAnalyzer() {
        graph = TinkerGraph.open();
        graph.createIndex("Kind", Vertex.class);
    }

    public JarAnalyzer(TinkerGraph graph) {
        this.graph = graph;
        this.graph.createIndex("Kind", Vertex.class);
    }

    public void toGraph(JarInformation j) {
        GraphTraversalSource graphTraversalSource = graph.traversal();

        Vertex jar = graphTraversalSource.addV()
                .property("Kind", "Jar")
                .property("ArtifactId", j.getArtifactId())
                .property("Version", j.getVersion())
                .property("GroupId", j.getGroupId())
                .next();

        for (PackageInfo p : j.getPackages()) {
            Vertex pkg = graphTraversalSource.addV()
                    .property("Kind", "Package")
                    .property("Name", p.getName())
                    .next();

            graphTraversalSource.addE("ContainsPkg").from(jar).to(pkg).iterate();

            Map<Object, List<String>> innerClassQNameMap = new HashMap<>();

            for (ClassInfo c : p.getClasses()) {
                if (c.isAnonymousInnerClass()) {
                    continue;
                }

                Vertex cls = graphTraversalSource.addV()
                        .property("Kind", "Class")
                        .property("isAbstract", c.isAbstract())
                        .property("isInterface", c.isInterface())
                        .property("isEnum", c.isEnum())
                        .property("Name", c.getName())
                        .property("isPublic", c.isPublic())
                        .property("isPrivate", c.isPrivate())
                        .property("isProtected", c.isProtected())
                        .property("QName", c.getQualifiedName())
                        .property("packageName", c.getPackageName())
                        .property("isInnerClass", c.isInnerClass())
                        .property("isAnonymousInnerClass", c.isAnonymousInnerClass())
                        .property("typeDescriptor", c.getType().getDescriptor())
                        .next();

                graphTraversalSource.addE("Contains").from(pkg).to(cls).iterate();

                if (!c.getSuperClassName().isEmpty()) {
                    Vertex superClass = graphTraversalSource.addV()
                            .property("Kind", "SuperClass")
                            .property("Name", c.getSuperClassName())
                            .next();

                    graphTraversalSource.addE("extends").from(cls).to(superClass).iterate();
                }

                innerClassQNameMap.put(cls.id(), c.getInnerClassNameList());

                c.getSuperInterfaceNames()
                        .forEach(e -> {
                            Vertex superInterface = graphTraversalSource.addV()
                                    .property("Kind", "SuperInterface")
                                    .property("Name", e).next();

                            graphTraversalSource.addE("implements").from(cls).to(superInterface).iterate();
                        });

                c.getMethods()
                        .forEach(m -> {
                            Vertex x = graphTraversalSource.addV()
                                    .property("Kind", "Method")
                                    .property("Name", m.getName())
                                    .property("isAbstract", m.isAbstract())
                                    .property("isConstructor", m.isConstructor())
                                    .property("isStatic", m.isStatic())
                                    .property("isPublic", m.isPublic())
                                    .property("isPrivate", m.isPrivate())
                                    .property("isProtected", m.isProtected())
                                    .property("isSynchronized", m.isSynchronized())
                                    .property("isVarargs", m.isVarargs())
                                    .property("className", m.getClassName())
                                    .property("returnTypeDescriptor", m.getReturnTypeAsType().getDescriptor())
                                    .next();

                            if (Objects.nonNull(m.getInternalClassConstructorSuffix())) {
                                graphTraversalSource.V(x.id())
                                        .property("internalClassConstructorSuffix", m.getInternalClassConstructorSuffix())
                                        .next();
                            }

                            for (Type type : m.getArgumentTypes()) {
                                graphTraversalSource.V(x.id())
                                        .property(VertexProperty.Cardinality.list, "argumentTypeDescriptorList",
                                                type.getDescriptor()).next();
                            }

                            for (String thrownInternalClassName : m.getThrownInternalClassNames()) {
                                graphTraversalSource.V(x.id())
                                        .property(VertexProperty.Cardinality.set, "thrownInternalClassNames",
                                                thrownInternalClassName).next();
                            }

                            graphTraversalSource.addE("Declares").from(cls).to(x).iterate();
                        });

                c.getFields()
                        .forEach(f -> {
                            Vertex field = graphTraversalSource.addV()
                                    .property("Kind", "Field")
                                    .property("Name", f.getName())
                                    .property("isPublic", f.isPublic())
                                    .property("isPrivate", f.isPrivate())
                                    .property("isProtected", f.isProtected())
                                    .property("isStatic", f.isStatic())
                                    .property("returnTypeDescriptor", f.getType().getDescriptor())
                                    .property("signature", f.getSignature())
                                    .next();

                            graphTraversalSource.addE("Declares").from(cls).to(field).iterate();
                        });
            }

            innerClassQNameMap.forEach((classVertexId, innerClassQNameList) -> {
                if (!innerClassQNameList.isEmpty()) {
                    Vertex classVertex = graphTraversalSource.V(classVertexId).next();

                    innerClassQNameList.forEach(innerClassQName -> {
                        graphTraversalSource.V(jar.id())
                                .out("ContainsPkg")
                                .hasId(pkg.id())
                                .out("Contains")
                                .has("Kind", "Class")
                                .has("QName", innerClassQName)
                                .addE("ContainsInnerClass").from(classVertex)
                                .iterate();
                    });
                }
            });

        }
    }

    public static Map<String, Tuple3<List<String>, List<String>, Boolean>> getHierarchyCompositionMap(JarInformation j) {

        return j.getPackages().stream().flatMap(x -> x.getClasses().stream())
                .collect(toMap(ClassInfo::getQualifiedName
                        , x -> Tuple.of(
                                concat(ofNullable(x.getSuperClassName()), x.getSuperInterfaceNames().stream()).collect(toList())
                                , x.getFields().stream().map(f -> f.getType().toString()).collect(toList())
                                , x.isEnum())));
    }

    public Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName,
                                                                Repository repository) {
        Set<Tuple3<String, String, String>> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom(commitId, projectName, repository);

        Set<Tuple3<String, String, String>> jarArtifactInfoSetForLoad = jarArtifactInfoSet.stream()
                .filter(jarArtifactInfo -> !isJarExists(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3))
                .collect(Collectors.toSet());

        jarArtifactInfoSetForLoad.forEach(jarArtifactInfo -> {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3);

            toGraph(jarInformation);
        });

        if (jarArtifactInfoSetForLoad.size() > 0) {
            storeClassStructureGraph();
        }

        return jarArtifactInfoSet;
    }

    public void loadJar(String groupId, String artifactId, String version) {
        if (!isJarExists(groupId, artifactId, version)) {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(groupId, artifactId, version);

            toGraph(jarInformation);
            storeClassStructureGraph();
        }
    }

    public void storeClassStructureGraph() {
        logger.info("storing graph");

        graph.traversal().io(getJarStoragePath().toString())
                .with(IO.writer, IO.gryo)
                .write().iterate();
    }

    public void loadClassStructureGraph() {
        logger.info("loading graph");

        graph.traversal().io(getJarStoragePath().toString())
                .with(IO.reader, IO.gryo)
                .read().iterate();
    }

    public void createClassStructureGraphForJavaJars() {
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
                        jarToGraph(jarFile, path.getFileName().toString(), "Java", javaVersion);
                    }
                } catch (Exception e) {
                    logger.error("Could not open the JAR", e);
                }
            }
        }
    }

    public void jarToGraph(JarFile jarFile, String groupId, String artifactId, String version) {
        JarInformation ji = new JarInformation(jarFile, groupId, artifactId, version);
        toGraph(ji);
    }

    private boolean isJarExists(String groupId, String artifactId, String version) {
        return graph.traversal().V()
                .has("Kind", "Jar")
                .has("GroupId", groupId)
                .has("ArtifactId", artifactId)
                .has("Version", version)
                .toSet().size() > 0;
    }

}
