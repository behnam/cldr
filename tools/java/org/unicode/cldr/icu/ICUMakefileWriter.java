// Copyright 2009 Google Inc. All Rights Reserved.

package org.unicode.cldr.icu;

import org.unicode.cldr.icu.DeprecatedConverter.MakefileInfo;
import org.unicode.cldr.util.CLDRFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

class ICUMakefileWriter {
  private final ICULog log;
  
  ICUMakefileWriter(ICULog log) {
    this.log = log;
  }
  
  static enum MakefileType {
    RES("main", "GENRB"),
    COL("collation", "COLLATION"),
    BRK("brkitr", "BRK_RES"),
    RBNF("rbnf", "RBNF");
    
    final String treeName;
    final String stub;
    final String shortstub;
    
    MakefileType(String treeName, String stub) {
      this.treeName = treeName;
      this.stub = stub;
      this.shortstub = toString().toLowerCase();
    }
    
    static ICUMakefileWriter.MakefileType forTreeName(String treeName) {
      for (ICUMakefileWriter.MakefileType t : values()) {
        if (t.treeName.equals(treeName)) {
          return t;
        }
      }
      throw new IllegalArgumentException("Unknown tree name in writeResourceMakefile: " + 
          treeName);
    }
  }
  
  public void write(ICUMakefileWriter.MakefileType mt, MakefileInfo info, File dstDir) {
    String resfiles_mk_name = dstDir + "/" + mt.shortstub + "files.mk";
    
    String generatedAliasText = fileMapToList(info.generatedAliasFiles);
    String aliasFilesText = fileMapToList(info.aliasFromFiles);
    String ctdFilesText = createFileList(info.ctdFiles);
    String brkFilesText = createFileList(info.brkFiles);
    String emptyFilesText = info.emptyFromFiles.isEmpty() ? null : fileMapToList(info.emptyFromFiles);
    String inFilesText = fileMapToList(info.fromFiles);

    try {
      log.info("Writing ICU build file: " + resfiles_mk_name);

      Calendar c = Calendar.getInstance();
      int year = c.get(Calendar.YEAR);
      String localmk = mt.shortstub + "local.mk";

      PrintStream ps = new PrintStream(new FileOutputStream(resfiles_mk_name));
      ps.println("# *   Copyright (C) 1998-" + year + ", International Business Machines");
      ps.println("# *   Corporation and others.  All Rights Reserved.");
      ps.println(mt.stub + "_CLDR_VERSION = " + CLDRFile.GEN_VERSION);
      ps.println("# A list of txt's to build");
      ps.println("# Note: ");
      ps.println("#");
      ps.println("#   If you are thinking of modifying this file, READ THIS.");
      ps.println("#");
      ps.println("# Instead of changing this file [unless you want to check it back in],");
      ps.println("# you should consider creating a '" + localmk + "' file in this same directory.");
      ps.println("# Then, you can have your local changes remain even if you upgrade or");
      ps.println("# reconfigure ICU.");
      ps.println("#");
      ps.println("# Example '" + localmk + "' files:");
      ps.println("#");
      ps.println("#  * To add an additional locale to the list: ");
      ps.println("#    _____________________________________________________");
      ps.println("#    |  " + mt.stub + "_SOURCE_LOCAL =   myLocale.txt ...");
      ps.println("#");
      ps.println("#  * To REPLACE the default list and only build with a few");
      ps.println("#    locales:");
      ps.println("#    _____________________________________________________");
      ps.println("#    |  " + mt.stub + "_SOURCE = ar.txt ar_AE.txt en.txt de.txt zh.txt");
      ps.println("#");
      ps.println("#");
      ps.println("# Generated by LDML2ICUConverter, from LDML source files. ");
      ps.println();

      ps.println("# Aliases without a corresponding xx.xml file (see " + DeprecatedConverter.SOURCE_INFO + ")");
      ps.println(mt.stub + "_SYNTHETIC_ALIAS =" + generatedAliasText);
      ps.println();
      ps.println();

      ps.println("# All aliases (to not be included under 'installed'), but not including root.");
      ps.println(mt.stub + "_ALIAS_SOURCE = $(" + mt.stub + "_SYNTHETIC_ALIAS)" + aliasFilesText);
      ps.println();
      ps.println();

      if (ctdFilesText != null) {
        ps.println("# List of compact trie dictionary files (ctd).");
        ps.println("BRK_CTD_SOURCE = " + ctdFilesText);
        ps.println();
        ps.println();
      }

      if (brkFilesText != null) {
        ps.println("# List of break iterator files (brk).");
        ps.println("BRK_SOURCE = " + brkFilesText);
        ps.println();
        ps.println();
      }

      if (emptyFilesText != null) {
        ps.println("# Empty locales, used for validSubLocale fallback.");
        ps.println(mt.stub + "_EMPTY_SOURCE =" + emptyFilesText);
        ps.println();
        ps.println();
      }

      ps.println("# Ordinary resources");
      if (emptyFilesText == null) {
        ps.print(mt.stub + "_SOURCE =" + inFilesText);
      } else {
        ps.print(mt.stub + "_SOURCE = $(" + mt.stub + "_EMPTY_SOURCE)" + inFilesText);
      }
      ps.println();
      ps.println();

      ps.close();
    } catch(IOException e) {
      log.error("While writing " + resfiles_mk_name, e);
      System.exit(1);
    }
  }
  
  private static final String LINESEP = System.getProperty("line.separator");
  
  private static String createFileList(List<String> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (String string : list) {
      sb.append(" ").append(string);
    }
    return sb.toString();
  }

  private static String fileMapToList(Map<String, File> files) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    for (File f : files.values()) {
      if ((++i % 5) == 0) {
        out.append("\\").append(LINESEP);
      }
      out.append(" ").append(f.getName().substring(0, f.getName().indexOf('.'))).append(".txt");
    }
    return out.toString();
  }
}