package com.flora.ramet.idea.highlighter;

import com.flora.ramet.idea.RametLanguage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Ramet 模板语言的配色设置页面。
 */
public class RametColorSettingsPage implements ColorSettingsPage {

    private static final String DEMO_TEXT = """
            <#meta>
            @Param{
              pkg: "com.example.gen",
              entities: ["User", "Order", "Product"],
              Author: { name: "Author", type: "String", indexed: false },
              Title: { name: "Title", type: "String", indexed: true },
              Price: { name: "Price", type: "double", indexed: true }
            }
            @Cartesian{
              T: concatList(
                     multiCombination([Author, Title, Price], 1),
                     multiCombination([Author, Title], 2))
            }
            @Path{ concat(javaPackageToPath(pkg), "/", T.name, "Entity.java") }
            @Config{ autoWarning: true }
            @SkipWhen{ equals(T.type, "double") }
            </#meta>
            package ${pkg};
            
            <#macro entityDef type name>
            private ${type} ${name};
            public ${type} get${capitalize(name)}() { return ${name}; }
            public void set${capitalize(name)}(${type} ${name}) { this.${name} = ${name}; }
            </#macro>
            
            public class ${T.name}Entity {
            
                <#for i:range(1, length(entities))>
                private String ${entities[minus(i,1)]}Id;
                </#for>
            
                <#if T.indexed>
                <@entityDef T.type "value"/>
                <#else>
                <@entityDef "Object" "rawValue"/>
                </#if>
            
                <#include "common-methods.ramet">
            
                @Override
                public String toString() {
                    return "<#" + "${T.name}" + " id=" + id + ">";
                }
            }
            """;

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new RametSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return DEMO_TEXT;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public @NotNull AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return new AttributesDescriptor[]{
                new AttributesDescriptor("Ramet: Variable", RametSyntaxHighlighter.RAMET_VARIABLE),
                new AttributesDescriptor("Ramet: Bracket", RametSyntaxHighlighter.RAMET_BRACKET),
                new AttributesDescriptor("Ramet: Comment", RametSyntaxHighlighter.RAMET_COMMENT),
                new AttributesDescriptor("Text: Default", RametSyntaxHighlighter.TEXT_DEFAULT),
                new AttributesDescriptor("Text: String", RametSyntaxHighlighter.TEXT_STRING),
                new AttributesDescriptor("Text: Number", RametSyntaxHighlighter.TEXT_NUMBER),
                new AttributesDescriptor("Text: Annotation", RametSyntaxHighlighter.TEXT_ANNOTATION),
                new AttributesDescriptor("Text: Function", RametSyntaxHighlighter.TEXT_FUNCTION),
                new AttributesDescriptor("Text: Operator", RametSyntaxHighlighter.TEXT_OPERATOR),
                new AttributesDescriptor("Text: Angle", RametSyntaxHighlighter.TEXT_ANGLE),
        };
    }

    @Override
    public @NotNull ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Ramet 模板";
    }
}
