package org.unicode.cldr.util;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSet;

public enum DtdType {
    ldml("common/dtd/ldml.dtd", null, null,
        "main",
        "annotations",
        "casing",
        "collation",
        "rbnf",
        "segments",
        "subdivisions"),
    ldmlICU("common/dtd/ldmlICU.dtd", ldml),
    supplementalData("common/dtd/ldmlSupplemental.dtd", null, null,
        "supplemental", 
        "transforms", 
        "validity"),
    ldmlBCP47("common/dtd/ldmlBCP47.dtd", "1.7.2", null, 
        "bcp47"),
    keyboard("keyboards/dtd/ldmlKeyboard.dtd", "22.1", null, 
        "../keyboards"),
    platform("keyboards/dtd/ldmlPlatform.dtd", "22.1", null,
        "../keyboards");

    static Pattern FIRST_ELEMENT = PatternCache.get("//([^/\\[]*)");

    public final String dtdPath;
    public final DtdType rootType;
    public final String firstVersion;
    public final Set<String> directories;

    private DtdType(String dtdPath) {
        this(dtdPath,null,null);
    }

    private DtdType(String dtdPath, DtdType realType) {
        this(dtdPath,null,realType);
    }

    private DtdType(String dtdPath, String firstVersion, DtdType realType, String... directories) {
        this.dtdPath = dtdPath;
        this.rootType = realType == null ? this : realType;
        this.firstVersion = firstVersion;
        this.directories = ImmutableSet.copyOf(directories);
    }

    public static DtdType fromPath(String elementOrPath) {
        Matcher m = FIRST_ELEMENT.matcher(elementOrPath);
        m.lookingAt();
        return DtdType.valueOf(m.group(1));
    }

    public String header(Class<?> generatedBy) {
        String gline = "";
        if (generatedBy != null) {
            gline = "\n\tGENERATED DATA — do not manually update!"
                + "\n\t\tGenerated by tool:\t" + generatedBy.getSimpleName() + "\n";
            for (Annotation annotation : generatedBy.getAnnotations()) {
                if(annotation instanceof CLDRTool){
                    gline += "\t\tTool documented on:\t" + ((CLDRTool) annotation).url() + "\n";
                    break;
                }
            }
        }

        return "<?xml version='1.0' encoding='UTF-8' ?>\n"
        + "<!DOCTYPE " + this + " SYSTEM '../../" + dtdPath + "'>\n" // "common/dtd/ldmlSupplemental.dtd"
        + "<!--\n"
        + "\t© 1991-2015 Unicode, Inc.\n"
        + "\tUnicode and the Unicode Logo are registered trademarks of Unicode, Inc. in the U.S. and other countries.\n"
        + "\tFor terms of use, see http://www.unicode.org/copyright.html.\n"
        + "\tCLDR data files are interpreted according to the LDML specification (http://unicode.org/reports/tr35/).\n"
        + gline
        + " -->\n"
        + "<" + this + ">\n";
    }
}