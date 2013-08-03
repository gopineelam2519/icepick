package com.github.frankiesardo.icepick.annotation;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes("com.github.frankiesardo.icepick.annotation.Icicle")
public class IcicleProcessor extends AbstractProcessor {

    public static final String SUFFIX = "$$Icicle";

    @Override
    public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment env) {
        Set<? extends Element> elements = env.getElementsAnnotatedWith(Icicle.class);
        Map<TypeElement, Set<IcicleField>> fieldsByType = new HashMap<TypeElement, Set<IcicleField>>();
        groupFieldsByType(elements, fieldsByType);
        writeHelpers(fieldsByType);
        return true;
    }

    private void groupFieldsByType(Set<? extends Element> elements, Map<TypeElement, Set<IcicleField>> fieldsByType) {
        IcicleConverter icicleConverter = new IcicleConverter(new IcicleAssigner(processingEnv.getTypeUtils(), processingEnv.getElementUtils()));
        for (Element element : elements) {
            if (element.getModifiers().contains(Modifier.FINAL) ||
                    element.getModifiers().contains(Modifier.STATIC) ||
                    element.getModifiers().contains(Modifier.PRIVATE)) {
                error(element, "Field must not be private, static or final");
                continue;
            }
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            Set<IcicleField> fields = fieldsByType.get(enclosingElement);
            if (fields == null) {
                fields = new LinkedHashSet<IcicleField>();
                fieldsByType.put(enclosingElement, fields);
            }
            String fieldName = element.getSimpleName().toString();
            String fieldType = element.asType().toString();
            String fieldCommand = icicleConverter.convert(element.asType().toString());
            fields.add(new IcicleField(fieldName, fieldType, fieldCommand));
        }
    }

    private void writeHelpers(Map<TypeElement, Set<IcicleField>> fieldsByType) {
        for (Map.Entry<TypeElement, Set<IcicleField>> entry : fieldsByType.entrySet()) {
            TypeElement classElement = entry.getKey();
            IcicleAssigner icicleAssigner = new IcicleAssigner(processingEnv.getTypeUtils(), processingEnv.getElementUtils());
            boolean isView = icicleAssigner.isAssignable(classElement.toString(), "android.view.View");

            try {
                JavaFileObject jfo = processingEnv.getFiler().createSourceFile(classElement.getQualifiedName() + SUFFIX, classElement);
                Writer writer = jfo.openWriter();

                IcicleWriter icicleWriter = isView ? new IcicleViewWriter(writer, SUFFIX) : new IcicleFragmentActivityWriter(writer, SUFFIX);
                icicleWriter.writeClass(classElement, entry.getValue());
            } catch (IOException e) {
                error(classElement, "Impossible to create %. Reason: %" + classElement.getQualifiedName() + SUFFIX, e);
            }
        }
    }

    private void error(Element element, String message, Object... args) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(message, args), element);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
