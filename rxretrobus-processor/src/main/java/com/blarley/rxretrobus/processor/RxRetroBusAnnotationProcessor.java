package com.blarley.rxretrobus.processor;

import com.blarley.rxretrobus.annotations.CachedEvent;
import com.blarley.rxretrobus.annotations.FireAndForgetEvent;
import com.blarley.rxretrobus.annotations.GenerateEvents;
import com.blarley.rxretrobus.annotations.UncachedEvent;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("com.blarley.rxretrobus.annotations.GenerateEvents")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class RxRetroBusAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        List<GeneratedClass> generatedClasses = new ArrayList<>();
        String generatedPrefix = "RxRetroBus_";

        // Get the Retrofit interfaces annotated with GenerateEvents
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateEvents.class)) {

            GenerateEvents generateEvents = element.getAnnotation(GenerateEvents.class);

            String baseType = element.asType().toString();
            String baseClassName = element.getSimpleName().toString();
            String generatedClassName = generatedPrefix + baseClassName;

            // Add generated Class to create Clients file
            generatedClasses.add(new GeneratedClass(generatedClassName, generateEvents.retrofit()));

            String baseUrl = generateEvents.baseUrl();

            // Package and imports
            StringBuilder builder = new StringBuilder()
                    .append("package com.blarley.rxretrobus.processor.generated;\n\n")
                    .append("import com.blarley.rxretrobus.*;\n" +
                            "import com.blarley.rxretrobus.RxRetroBus;\n");

            if (generateEvents.retrofit()) {
                builder.append("import retrofit2.Retrofit;\n");
            }

            // Begin class definition
            builder.append("\npublic class " + generatedClassName + " {\n\n");

            // Retrofit client impl
            builder.append("\tprivate " + baseType + " client;\n");
            builder.append("\tprivate RxRetroBus bus;\n\n");

            if (generateEvents.retrofit()) {
                // Constructor - builds Retrofit client
                builder.append("\tpublic " + generatedClassName + "(Retrofit.Builder retrofitBuilder, RxRetroBus bus) { \n" +
                        "\t\tthis.client = retrofitBuilder.baseUrl(\"" + baseUrl + "\")\n" +
                        "\t\t\t\t.build()\n" +
                        "\t\t\t\t.create(" + baseType + ".class);\n");
            } else {
                builder.append("\tpublic " + generatedClassName + "(RxRetroBus bus) { \n" +
                        "\t\tthis.client = new " + baseType + "();\n");
            }

            builder.append("\t\tthis.bus = bus;\n\t}\n\n");

            // Get Annotated methods within the class - the builds the method used to make calls
            for (Element subElement : element.getEnclosedElements()) {

                // ExecutableElements represent methods (among other things) - TODO: Figure out how this can break
                if (subElement instanceof ExecutableElement) {

                    // TODO: 7/12/17 - Validate that multiple event types are not on a method
                    FireAndForgetEvent fireAndForgetEvent = subElement.getAnnotation(FireAndForgetEvent.class);
                    CachedEvent cachedEvent = subElement.getAnnotation(CachedEvent.class);
                    UncachedEvent uncachedEvent = subElement.getAnnotation(UncachedEvent.class);

                    if (fireAndForgetEvent != null || cachedEvent != null || uncachedEvent != null) {

                        // Cast to ExecutableElement in order to get Parameters
                        ExecutableElement method = (ExecutableElement) subElement;
                        String methodName = method.getSimpleName().toString();

                        // Begin definition of method
                        builder.append("\tpublic void " + methodName + "(");

                        // Append parameters to method definition - TODO: Figure out how this can break
                        String delim = "";
                        StringBuilder params = new StringBuilder();
                        StringBuilder args = new StringBuilder();
                        for (VariableElement param : method.getParameters()) {
                            params.append(delim)
                                    .append(param.asType() + " ")
                                    .append(param.getSimpleName().toString());

                            args.append(delim)
                                    .append(param.getSimpleName().toString());
                            delim = ", ";
                        }

                        // Append the parameters to the method definition and open declaration
                        builder.append(params)
                                .append(") {\n");

                        // Need to strip off the Observable and get parameterized class
                        // TODO: Is this a better way to do this?
                        String observable = method.getReturnType().toString();
                        Pattern regex = Pattern.compile("<(.*?)>");
                        Matcher matcher = regex.matcher(observable);
                        String innerClass = "";
                        while (matcher.find()) {
                            innerClass += matcher.group(1);
                        }

                        builder.append("\t\tbus.addObservable(client." + methodName + "(")
                                .append(args)
                                .append("), ")
                                .append(innerClass + ".class, ");

                        if (fireAndForgetEvent != null) {
                            builder.append("new FireAndForgetEvent(")
                                    .append("\"" + fireAndForgetEvent.tag() + "\", ")
                                    .append(fireAndForgetEvent.debounce() + "));\n");

                        } else if (cachedEvent != null) {
                            builder.append("new CachedEvent(")
                                    .append("\"" + cachedEvent.tag() + "\", ")
                                    .append(cachedEvent.debounce() + "));\n");

                        } else if (uncachedEvent != null) {
                            builder.append("new UncachedEvent(")
                                    .append("\"" + uncachedEvent.tag() + "\", ")
                                    .append(uncachedEvent.debounce() + ", ")
                                    .append(uncachedEvent.sticky() + "));\n");
                        }

                        // End method definition
                        builder.append("\t}\n\n");
                    }
                }
            }

            // End Class definition
            builder.append("}\n");

            // Write the file
            try {
                JavaFileObject source = processingEnv.getFiler()
                        .createSourceFile(
                                "com.blarley.rxretrobus.processor.generated."
                                        + generatedClassName);

                Writer writer = source.openWriter();
                writer.write(builder.toString());
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Clients String builder
        StringBuilder clientsFile = new StringBuilder()
                .append("package com.blarley.rxretrobus.processor.generated;\n\n")
                .append("import retrofit2.Retrofit;\n\n")
                .append("import com.blarley.rxretrobus.RxRetroBus;\n\n")
                .append("public class Clients {\n");

        StringBuilder constructorDefinition = new StringBuilder();

        // Append the instance fields
        for (GeneratedClass generatedClass : generatedClasses) {
            String[] str = generatedClass.getName().split("_");
            String baseClassName = str[1];
            clientsFile.append("\tpublic ")
                    .append(generatedClass.getName())
                    .append(" ")
                    .append(baseClassName)
                    .append(";\n");

            constructorDefinition.append("\t\tthis.")
                    .append(baseClassName)
                    .append(" = new ")
                    .append(generatedClass.getName())
                    .append(generatedClass.isRetrofitEnabled()
                            ? "(retrofitBuilder, bus);\n"
                            : "(bus);\n");
        }

        // Append the constructor declaration
        clientsFile.append("\n\tpublic Clients(Retrofit.Builder retrofitBuilder, RxRetroBus bus) {\n");

        // Append the constructor definition
        clientsFile.append(constructorDefinition);

        // Close the constructor and class
        clientsFile.append("\t}\n")
                .append("}");

        // Write the file
        try {
            JavaFileObject source = processingEnv.getFiler()
                    .createSourceFile(
                            "com.blarley.rxretrobus.processor.generated.Clients");
            Writer writer = source.openWriter();
            writer.write(clientsFile.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
        }

        return true;
    }


    class GeneratedClass {
        private String name;
        private Boolean retrofitEnabled;

        public GeneratedClass(String name, Boolean retrofitEnabled) {
            this.name = name;
            this.retrofitEnabled = retrofitEnabled;
        }


        public String getName() {
            return name;
        }


        public Boolean isRetrofitEnabled() {
            return retrofitEnabled;
        }
    }
}
