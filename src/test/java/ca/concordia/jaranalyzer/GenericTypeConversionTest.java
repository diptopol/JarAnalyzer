package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.typeInfo.QualifiedTypeInfo;
import ca.concordia.jaranalyzer.Models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.*;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 6/22/2021 12:17 PM
 */
public class GenericTypeConversionTest {

    @Test
    public void testSignatureForFormalTypeReturn() {
        String methodSignature = "()Ljava/util/Set<Ljava/util/Map$Entry<TK;TV;>;>;";
        SignatureReader signatureReader = new SignatureReader(methodSignature);

        Map<String, TypeInfo> map = new HashMap<>();
        map.put("K", new QualifiedTypeInfo("java.lang.String"));
        map.put("V", new QualifiedTypeInfo("java.lang.String"));

        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        assert "[]".equals(Arrays.asList(genericTypeResolutionAdapter.getMethodArgumentTypes()).toString())
                && "java.util.Set".equals(genericTypeResolutionAdapter.getMethodReturnType().getClassName());
    }

    @Test
    public void testSignatureForExtendedFormalTypeArgument() {
        String methodSignature = "(Ljava/util/Map<+TK;+TV;>;Z)V";

        SignatureReader signatureReader = new SignatureReader(methodSignature);

        Map<String, TypeInfo> map = new HashMap<>();
        map.put("K", new QualifiedTypeInfo("java.lang.Object"));
        map.put("V", new QualifiedTypeInfo("java.lang.Object"));

        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        List<String> argumentTypeClassNameList = Arrays.stream(genericTypeResolutionAdapter.getMethodArgumentTypes())
                .map(Type::getClassName)
                .collect(Collectors.toList());

        assert "[java.util.Map, boolean]".equals(argumentTypeClassNameList.toString())
                && "void".equals(genericTypeResolutionAdapter.getMethodReturnType().getClassName());
    }

    @Test
    public void testRemovalOfFormalTypeParameter() {
        String methodSignature = "<T::Ljava/util/EventListener;>(Ljava/lang/Class<TT;>;TT;)V";

        SignatureReader signatureReader = new SignatureReader(methodSignature);

        Map<String, TypeInfo> map = new HashMap<>();
        map.put("T", new QualifiedTypeInfo("org.jfree.chart.plot.dial.DialLayerChangeListener"));

        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        List<String> argumentTypeClassNameList = Arrays.stream(genericTypeResolutionAdapter.getMethodArgumentTypes())
                .map(Type::getClassName)
                .collect(Collectors.toList());

        assert "[java.lang.Class, org.jfree.chart.plot.dial.DialLayerChangeListener]".equals(argumentTypeClassNameList.toString())
                && "void".equals(genericTypeResolutionAdapter.getMethodReturnType().getClassName());
    }

    @Test
    public void testForArrayAsArgument() {
        String methodSignature = "([TE;)V";

        SignatureReader signatureReader = new SignatureReader(methodSignature);

        Map<String, TypeInfo> map = new HashMap<>();
        map.put("E", new QualifiedTypeInfo("java.lang.String"));

        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        List<String> argumentTypeClassNameList = Arrays.stream(genericTypeResolutionAdapter.getMethodArgumentTypes())
                .map(Type::getClassName)
                .collect(Collectors.toList());

        assert "[java.lang.String[]]".equals(argumentTypeClassNameList.toString())
                && "void".equals(genericTypeResolutionAdapter.getMethodReturnType().getClassName());
    }

    @Test
    public void testNonFormalTypeArgument() {
        String signature = "(Ljava/lang/Object;)TV;";

        Map<String, TypeInfo> map = new HashMap<>();
        map.put("V", new QualifiedTypeInfo("java.lang.String"));

        SignatureReader signatureReader = new SignatureReader(signature);
        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        assert "(Ljava/lang/Object;)Ljava/lang/String;".equals(genericTypeResolutionAdapter.getSignatureWriter().toString());
    }

    @Test
    public void testFormalTypParameterExtractionFromClassSignature() {
        String classSignature = "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/util/Map<TK;TV;>;Ljava/lang/Cloneable;Ljava/io/Serializable;";

        SignatureReader signatureReader = new SignatureReader(classSignature);

        ClassSignatureFormalTypeParameterExtractor classSignatureFormalTypeParameterExtractor =
                new ClassSignatureFormalTypeParameterExtractor();

        signatureReader.accept(classSignatureFormalTypeParameterExtractor);

        assert ("[FormalTypeParameterInfo{typeParameter='K', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                " FormalTypeParameterInfo{typeParameter='V', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]")
                .equals(classSignatureFormalTypeParameterExtractor.getTypeArgumentList().toString());
    }

    @Test
    public void testFormalTypParameterExtractionFromClassSignatureWithInterfaceBound() {
        String classSignature = "<K::Ljava/lang/Comparable<TK;>;>Ljava/lang/Object;Lorg/jfree/data/KeyedValues<TK;>;Ljava/lang/Cloneable;Lorg/jfree/chart/util/PublicCloneable;Ljava/io/Serializable;";

        SignatureReader signatureReader = new SignatureReader(classSignature);

        ClassSignatureFormalTypeParameterExtractor classSignatureFormalTypeParameterExtractor =
                new ClassSignatureFormalTypeParameterExtractor();

        signatureReader.accept(classSignatureFormalTypeParameterExtractor);

        assert "[FormalTypeParameterInfo{typeParameter='K', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Comparable'}}]"
                .equals(classSignatureFormalTypeParameterExtractor.getTypeArgumentList().toString());
    }

    @Test
    public void testFormalTypeParameterExtractionFromFieldSignature() {
        String signature = "Ljava/util/Map<Ljava/lang/Integer;Lorg/jfree/data/xy/XYDataset;>;";

        SignatureReader signatureReader = new SignatureReader(signature);
        FieldSignatureFormalTypeParameterExtractor fieldSignatureFormalTypeParameterExtractor = new FieldSignatureFormalTypeParameterExtractor();

        signatureReader.accept(fieldSignatureFormalTypeParameterExtractor);

        assert ("ParameterizedTypeInfo{qualifiedClassName='java.util.Map', isParameterized=false," +
                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.Integer'}," +
                " QualifiedTypeInfo{qualifiedClassName='org.jfree.data.xy.XYDataset'}]}")
                .equals(fieldSignatureFormalTypeParameterExtractor.getTypeInfo().toString());

    }

    @Test
    public void testNestedFormalTypeParameterExtractionFromFieldSignature() {
        String signature = "Ljava/util/Map<Lorg/jfree/data/flow/FlowKey<TK;>;Ljava/lang/Number;>;";

        SignatureReader signatureReader = new SignatureReader(signature);
        FieldSignatureFormalTypeParameterExtractor fieldSignatureFormalTypeParameterExtractor = new FieldSignatureFormalTypeParameterExtractor();

        signatureReader.accept(fieldSignatureFormalTypeParameterExtractor);

        assert ("ParameterizedTypeInfo{qualifiedClassName='java.util.Map', isParameterized=false," +
                " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='org.jfree.data.flow.FlowKey'," +
                " isParameterized=false, typeArgumentList=[FormalTypeParameterInfo{typeParameter='K'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}," +
                " QualifiedTypeInfo{qualifiedClassName='java.lang.Number'}]}")
                .equals(fieldSignatureFormalTypeParameterExtractor.getTypeInfo().toString());
    }

    @Test
    public void testFormalTypeParameterExtractionFromMethodSignature() {
        String signature = "<T::Ljava/util/EventListener;>(Ljava/lang/Class<TT;>;TT;)V";

        SignatureReader signatureReader = new SignatureReader(signature);
        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[ParameterizedTypeInfo{qualifiedClassName='java.lang.Class', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='T', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.util.EventListener'}}]}," +
                " FormalTypeParameterInfo{typeParameter='T', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.util.EventListener'}}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "VoidTypeInfo{}".equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testFormalTypeParameterExtractionFromMethodArguments() {
        String signature = "<P:Ljava/lang/String;R:Ljava/lang/Object;Q:Ljava/lang/Object;>(Ljava/util/Map<TP;TR;>;TQ;)V";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[ParameterizedTypeInfo{qualifiedClassName='java.util.Map', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='P', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.String'}}," +
                " FormalTypeParameterInfo{typeParameter='R', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}," +
                " FormalTypeParameterInfo{typeParameter='Q', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "VoidTypeInfo{}".equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testFormalTypeParameterExtractionForArrayArguments() {
        String signature = "<T:Ljava/lang/Object;>([TT;)[TT;";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[ArrayTypeInfo{elementTypeInfo=FormalTypeParameterInfo{typeParameter='T'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}, dimension=1}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert ("ArrayTypeInfo{elementTypeInfo=FormalTypeParameterInfo{typeParameter='T'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}, dimension=1}")
                .equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testMethodArgumentsAndReturnTypeExtractionForFormalTypeReturn() {
        String signature = "([Ljava/lang/Object;)TT;";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert "[ArrayTypeInfo{elementTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}, dimension=1}]"
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "FormalTypeParameterInfo{typeParameter='T', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}"
                .equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testReturnTypeExtractionForQualifiedTypeInfoAsTypeArg() {
        String signature = "(Ljava/lang/String;)Ljava/util/Iterator<Ljavax/imageio/ImageWriter;>;";
        SignatureReader signatureReader = new SignatureReader(signature);
        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert "[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}]"
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert ("ParameterizedTypeInfo{qualifiedClassName='java.util.Iterator', isParameterized=true," +
                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='javax.imageio.ImageWriter'}]}")
                .equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testTypeParameterAsFieldType() {
        String signature = "TR;";

        SignatureReader signatureReader = new SignatureReader(signature);
        FieldSignatureFormalTypeParameterExtractor fieldSignatureFormalTypeParameterExtractor = new FieldSignatureFormalTypeParameterExtractor();
        signatureReader.accept(fieldSignatureFormalTypeParameterExtractor);

        assert ("FormalTypeParameterInfo{typeParameter='R'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}")
                .equals(fieldSignatureFormalTypeParameterExtractor.getTypeInfo().toString());
    }

    @Test
    public void testClassNameReplacementForFormalParameterAsArgument() {
        String signature = "<K::Ljava/lang/Comparable<TK;>;>(Lorg/jfree/data/flow/FlowDataset<TK;>;TK;I)D";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[ParameterizedTypeInfo{qualifiedClassName='org.jfree.data.flow.FlowDataset', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='K', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Comparable'}}]}," +
                " FormalTypeParameterInfo{typeParameter='K', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Comparable'}}," +
                " PrimitiveTypeInfo{qualifiedClassName='int'}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "PrimitiveTypeInfo{qualifiedClassName='double'}".equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testNestedParameterizedTypesAsArgument() {
        String signature = "<T:Ljava/lang/Object;>(Ljava/util/List<+Ljava/lang/Comparable<-TT;>;>;TT;)I";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[ParameterizedTypeInfo{qualifiedClassName='java.util.List', isParameterized=true," +
                " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='java.lang.Comparable'," +
                " isParameterized=true, typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]}," +
                " FormalTypeParameterInfo{typeParameter='T'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "PrimitiveTypeInfo{qualifiedClassName='int'}".equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testArrayIndexReset() {
        String signature = "<T:Ljava/lang/Object;>([TT;IILjava/util/Comparator<-TT;>;)V";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[ArrayTypeInfo{elementTypeInfo=FormalTypeParameterInfo{typeParameter='T'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}, dimension=1}," +
                " PrimitiveTypeInfo{qualifiedClassName='int'}, PrimitiveTypeInfo{qualifiedClassName='int'}," +
                " ParameterizedTypeInfo{qualifiedClassName='java.util.Comparator', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "VoidTypeInfo{}".equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testWildCardTypeArgument() {
        String signature = "(Ljava/lang/String;[Ljava/lang/Class<*>;)Ljava/lang/reflect/Method;";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}," +
                " ArrayTypeInfo{elementTypeInfo=ParameterizedTypeInfo{qualifiedClassName='java.lang.Class'," +
                " isParameterized=true, typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}]}," +
                " dimension=1}]").equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "QualifiedTypeInfo{qualifiedClassName='java.lang.reflect.Method'}"
                .equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testInnerClassMethodArgument() {
        String signature = "(ITK;TV;Lcom/sun/beans/util/Cache<TK;TV;>.CacheEntry<TK;TV;>;)V";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[PrimitiveTypeInfo{qualifiedClassName='int'}," +
                " FormalTypeParameterInfo{typeParameter='K', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                " FormalTypeParameterInfo{typeParameter='V', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                " ParameterizedTypeInfo{qualifiedClassName='com.sun.beans.util.Cache.CacheEntry', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='K', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                " FormalTypeParameterInfo{typeParameter='V', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "VoidTypeInfo{}".equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testFormalTypeMultipleInterfaces() {
        String signature = "<T::Ljdk/nashorn/internal/ir/LexicalContextNode;:Ljdk/nashorn/internal/ir/Flags<TT;>;>(TT;)TT;";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[FormalTypeParameterInfo{typeParameter='T'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='jdk.nashorn.internal.ir.LexicalContextNode'}}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert ("FormalTypeParameterInfo{typeParameter='T'," +
                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='jdk.nashorn.internal.ir.LexicalContextNode'}}")
                .equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

    @Test
    public void testMultipleFormalTypeParameterWithTypeVariable() {
        String signature = "<R:Ljava/lang/Object;S:TR;>(Ljava/util/concurrent/CompletableFuture<TR;>;Ljava/util/concurrent/CompletableFuture<TS;>;Ljava/util/function/Function<-TR;+TT;>;Ljava/util/concurrent/CompletableFuture$OrApply<TR;TS;TT;>;)Z";
        SignatureReader signatureReader = new SignatureReader(signature);

        MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
        signatureReader.accept(methodArgumentExtractor);

        assert ("[ParameterizedTypeInfo{qualifiedClassName='java.util.concurrent.CompletableFuture', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='R', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}," +
                " ParameterizedTypeInfo{qualifiedClassName='java.util.concurrent.CompletableFuture', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='R', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}," +
                " ParameterizedTypeInfo{qualifiedClassName='java.util.function.Function', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='R', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                " FormalTypeParameterInfo{typeParameter='T', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}," +
                " ParameterizedTypeInfo{qualifiedClassName='java.util.concurrent.CompletableFuture$OrApply', isParameterized=true," +
                " typeArgumentList=[FormalTypeParameterInfo{typeParameter='R', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                " FormalTypeParameterInfo{typeParameter='R', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                " FormalTypeParameterInfo{typeParameter='T', baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]")
                .equals(methodArgumentExtractor.getArgumentList().toString());

        MethodReturnTypeExtractor methodReturnTypeExtractor = new MethodReturnTypeExtractor();
        signatureReader.accept(methodReturnTypeExtractor);

        assert "PrimitiveTypeInfo{qualifiedClassName='boolean'}".equals(methodReturnTypeExtractor.getReturnTypeInfo().toString());
    }

}
