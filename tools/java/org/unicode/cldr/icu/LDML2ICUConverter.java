/*
 ******************************************************************************
 * Copyright (C) 2004, International Business Machines Corporation and   *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
 */

package com.ibm.icu.dev.tool.cldr;

import com.ibm.icu.dev.tool.UOption;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.UCharacterIterator;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;

import javax.xml.transform.TransformerException;

/**
 * @author ram
 * 
 * Preferences - Java - Code Generation - Code and Comments
 */
public class LDML2ICUConverter {
    /**
     * These must be kept in sync with getOptions().
     */
    private static final int HELP1 = 0;
    private static final int HELP2 = 1;
    private static final int SOURCEDIR = 2;
    private static final int DESTDIR = 3;
    private static final int SPECIALSDIR = 4;
    private static final int WRITE_DEPRECATED = 5;
    private static final int WRITE_DRAFT = 6;
    private static final int SUPPLEMENTAL = 7;
    
    private static final UOption[] options = new UOption[] {
        UOption.HELP_H(),
        UOption.HELP_QUESTION_MARK(),
        UOption.SOURCEDIR(),
        UOption.DESTDIR(),
        UOption.create("specialsdir", 'p', UOption.REQUIRES_ARG),
        UOption.create("write-deprecated", 'w', UOption.NO_ARG),
        UOption.create("write-draft", 'f', UOption.NO_ARG),
        UOption.create("supplemental", 'l', UOption.NO_ARG),
    };
    
    private String  sourceDir         = null;
    private String  fileName          = null;
    private String  destDir           = null;
    private String  specialsDir       = null;
    private boolean writeDeprecated   = false;
    private boolean writeDraft        = false;
    private boolean writeSupplemental = false;
    
    private static final String LINESEP   = System.getProperty("line.separator");
    private static final String BOM       = "\uFEFF";
    private static final String CHARSET   = "UTF-8";
    private static final String COLON     = ":";
    
    private Document fullyResolvedDoc = null;
    private Document specialsDoc      = null;
    private String locName            = null;
    
    private static final boolean DEBUG = false;
    
    public static void main(String[] args) {
        LDML2ICUConverter cnv = new LDML2ICUConverter();
        cnv.processArgs(args);
    }
    
    private void usage() {
        System.out.println("\nUsage: LDML2ICUConverter [OPTIONS] [FILES]\n\n"+
            "This program is used to convert LDML files to ICU ResourceBundle TXT files.\n"+
            "Please refer to the following options. Options are not case sensitive.\n"+
            "Options:\n"+
            "-s or --sourcedir          source directory for files followed by path, default is current directory.\n" +
            "-d or --destdir            destination directory, followed by the path, default is current directory.\n" +
            "-p or --specialsdir        source directory for files containing special data followed by the path. None if not spcified\n"+
            "-w or --write-deprecated   write data for deprecated locales.\n"+
            "-f or --write-draft        write data for LDML nodes marked draft.\n"+
            "-l or --supplemental       read supplementalData.xml file from the given directory and write appropriate files to destination directory\n"+
            "-h or -? or --help         this usage text.\n"+
            "example: com.ibm.icu.dev.tool.cldr.LDML2ICUConverter -s xxx -d yyy en.xml");
        System.exit(-1);
    }
    
    private void processArgs(String[] args) {
        int remainingArgc = 0;
        try{
            remainingArgc = UOption.parseArgs(args, options);
        }catch (Exception e){
            System.err.println("ERROR: (parsing args): "+ e.toString());
            e.printStackTrace();
            usage();
        }
        if(args.length==0 || options[HELP1].doesOccur || options[HELP2].doesOccur) {
            usage();
        }
        
        if(options[SOURCEDIR].doesOccur) {
            sourceDir = options[SOURCEDIR].value;
        }
        if(options[DESTDIR].doesOccur) {
            destDir = options[DESTDIR].value;
        }
        if(options[SPECIALSDIR].doesOccur) {
            specialsDir = options[SPECIALSDIR].value;
        }
        if(options[WRITE_DEPRECATED].doesOccur) {
            writeDeprecated = true;
        }
        if(options[WRITE_DRAFT].doesOccur) {
            writeDraft = true;
        }
        if(options[SUPPLEMENTAL].doesOccur) {
            writeSupplemental = true;
        }
        
        if(destDir==null){
            destDir = ".";
        }
        if(remainingArgc==0){
            System.err.println("ERROR: Either the file name to be processed is not "+
                               "specified or the it is specified after the -t/-c \n"+
                               "option which has an optional argument. Try rearranging "+
                               "the options.");
            usage();
        }
        if(writeSupplemental==true){
            int lastIndex = args[0].lastIndexOf(File.separator, args[0].length()) + 1 /* add  1 to skip past the separator */; 
            fileName = args[0].substring(lastIndex, args[0].length());
            String xmlfileName = getFullPath(false,args[0]);
            try {

                System.out.println("INFO: Parsing document "+xmlfileName);
                
                Document doc = LDMLUtilities.parse(xmlfileName, false);
                // Create the Resource linked list which will hold the
                // data after parsing
                // The assumption here is that the top
                // level resource is always a table in ICU
                ICUResourceWriter.Resource res = parseSupplemental(doc);
                if(res!=null && ((ICUResourceWriter.ResourceTable)res).first!=null){
                    // write out the bundle
                    writeResource(res, xmlfileName);
                }
                
            }catch (Throwable se) {
                System.err.println(fileName + ": ERROR: (parsing supplemental) " + se.toString());
                se.printStackTrace();
                System.exit(1);
            }    
        }else{
            for (int i = 0; i < remainingArgc; i++) {
                long start = System.currentTimeMillis();
                int lastIndex = args[i].lastIndexOf(File.separator, args[i].length()) + 1 /* add  1 to skip past the separator */; 
                fileName = args[i].substring(lastIndex, args[i].length());
                String xmlfileName = getFullPath(false,args[i]);
                /*
                 * debugging code
                 * 
                 * try{ 
                 *      Document doc = LDMLUtilities.getFullyResolvedLDML(sourceDir,
                 *      args[i], false); 
                 *      OutputStreamWriter writer = new
                 *      OutputStreamWriter(new FileOutputStream("./"+File.separator+args[i]+"_debug.xml"),"UTF-8");
                 *      LDMLUtilities.printDOMTree(doc,new PrintWriter(writer));
                 *      writer.flush(); 
                 * }catch( IOException e){ 
                 *      //throw the exceptionaway .. this is for debugging 
                 * }
                 */ 
                // TODO : uncomment 
                System.out.println("INFO: Creating fully resolved LDML document for: " + sourceDir+File.separator+ args[i]);
                fullyResolvedDoc =  LDMLUtilities.getFullyResolvedLDML(sourceDir, args[i], false, false, false);
                if(specialsDir!=null){
                    locName = args[i];
                    System.out.println("INFO: Parsing LDML document for: " + specialsDir+File.separator+ args[i]);
                    specialsDoc = LDMLUtilities.parseAndResolveAliases(args[i], specialsDir, true);
                    if(specialsDoc != null) {
                        int index = locName.indexOf(".xml");
                        if(index > -1){
                            locName = locName.substring(0,index);
                        }
                    }
                    /*
                    try{ 
                        OutputStreamWriter writer = new
                        OutputStreamWriter(new FileOutputStream("./"+File.separator+args[i]+"_debug.xml"),"UTF-8");
                        LDMLUtilities.printDOMTree(fullyResolvedSpecials,new PrintWriter(writer));
                        writer.flush(); 
                    }catch( IOException e){ 
                          //throw the exceptionaway .. this is for debugging 
                    }
                    */
                }
                createResourceBundle(xmlfileName);
                long stop = System.currentTimeMillis();
                System.out.println("Time taken: "+ (stop-start));
            }
        }
    }
    private String getFullPath(boolean fileType, String fName){
        String str;
        int lastIndex1 = fName.lastIndexOf(File.separator, fName.length()) + 1/* add  1 to skip past the separator */; 
        int lastIndex2 = fName.lastIndexOf('.', fName.length());
        if (fileType == true) {
            if(lastIndex2 == -1){
                fName = fName.trim() + ".txt";
            }else{
                if(!fName.substring(lastIndex2).equalsIgnoreCase(".txt")){
                    fName =  fName.substring(lastIndex1,lastIndex2) + ".txt";
                }
            }
            if (destDir != null && fName != null) {
                str = destDir + File.separator + fName.trim();                   
            } else {
                str = System.getProperty("user.dir") + File.separator + fName.trim();
            }
        } else {
            if(lastIndex2 == -1){
                fName = fName.trim() + ".xml";
            }else{
                if(!fName.substring(lastIndex2).equalsIgnoreCase(".xml") && fName.substring(lastIndex2).equalsIgnoreCase(".xlf")){
                    fName = fName.substring(lastIndex1,lastIndex2) + ".xml";
                }
            }
            if(sourceDir != null && fName != null) {
                str = sourceDir + File.separator + fName;
            } else if (lastIndex1 > 0) {
                str = fName;
            } else {
                str = System.getProperty("user.dir") + File.separator + fName;
            }
        }
        return str;
    } 

    private void createResourceBundle(String xmlfileName) {
         try {

             System.out.println("INFO: Parsing LDML document for: "+xmlfileName);
             
             Document doc = LDMLUtilities.parse(xmlfileName, false);
             // Create the Resource linked list which will hold the
             // data after parsing
             // The assumption here is that the top
             // level resource is always a table in ICU
             ICUResourceWriter.Resource res = parseBundle(doc);
             if(res!=null && ((ICUResourceWriter.ResourceTable)res).first!=null){
                 // write out the bundle
                 writeResource(res, xmlfileName);
             }
             
             writeAliasedResource();
          }
         catch (Throwable se) {
             System.err.println(xmlfileName + ": ERROR: (parsing and writing) " + se.toString());
             se.printStackTrace();
             System.exit(1);
         }    
    }
    private void writeAliasedResource(){
        if(locName==null || writeDeprecated==false){
            return;
        }  
        String lang = (String) deprecatedMap.get(ULocale.getLanguage(locName));
        //System.out.println("In aliased resource");
        if(lang!=null){
            ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
            ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
            str.name = "\"%%ALIAS\"";
            if(lang.indexOf("_")<0){
                table.name = lang;
                String c = ULocale.getCountry(locName);
                if(c!=null && c.length()>0){
                    table.name = lang + "_" + c;
                }
                str.val = locName;
            }else{
                table.name = lang;
                str.val = ULocale.getLanguage(locName);
            }
            table.first = str;
            writeResource(table, "");
        }
        //System.out.println("exiting aliased resource");
    }
    
    private static final String LOCALE_SCRIPT   = "LocaleScript";
    private static final String NUMBER_ELEMENTS = "NumberElements";
    private static final String NUMBER_PATTERNS = "NumberPatterns";
    private static final String AM_PM_MARKERS   = "AmPmMarkers";
    private static final String DTP             = "DateTimePatterns";
    public static final String DTE              = "DateTimeElements";
    
    private static final HashMap keyNameMap = new HashMap();
    private static final HashMap deprecatedMap = new HashMap();
    static{
        keyNameMap.put("days", "dayNames");
        keyNameMap.put("months", "monthNames");
        keyNameMap.put("territories", "Countries");
        keyNameMap.put("languages", "Languages");
        keyNameMap.put("currencies", "Currencies");
        keyNameMap.put("variants", "Variants");
        keyNameMap.put("scripts", "Scripts");
        keyNameMap.put("keys", "Keys");
        keyNameMap.put("types", "Types");
        keyNameMap.put("version", "Version");
        keyNameMap.put("exemplarCharacters", "ExemplarCharacters");
        keyNameMap.put("timeZoneNames", "zoneStrings");
        keyNameMap.put("localizedPatternChars", "localPatternChars");
        keyNameMap.put("paperSize", "PaperSize");
        keyNameMap.put("measurementSystem", "MeasurementSystem");
        keyNameMap.put("fractions", "CurrencyData");
        deprecatedMap.put("he", "iw");
        deprecatedMap.put("id", "in");
        deprecatedMap.put("jv", "jw");
        deprecatedMap.put("nn", "no_NO_NY");
        //deprecatedMap.put("sr_Latn", "sh");// not sure how to handle this!
        deprecatedMap.put("yi", "ji");
        
        //TODO: "FX",  "RO",  "TP",  "ZR",   /* obsolete country codes */      
    }
    private ICUResourceWriter.Resource parseSupplemental(Node root){
        ICUResourceWriter.ResourceTable table = null;
        ICUResourceWriter.Resource current = null;
        StringBuffer xpath = new StringBuffer();
        xpath.append("//");
        xpath.append(LDMLConstants.SUPPLEMENTAL_DATA);
        
        int savedLength = xpath.length();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.SUPPLEMENTAL_DATA) ){
                if(LDMLUtilities.isNodeDraft(node) && writeDraft==false){
                    System.err.println("WARNING: The LDML file "+sourceDir+File.separator+locName+".xml is marked draft! Not producing ICU file. ");
                    System.exit(-1);
                }
                node=node.getFirstChild();
                continue;
            }else if (name.equals(LDMLConstants.SPECIAL)){
                /*
                 * IGNORE SPECIALS
                 * FOR NOW
                 */
                node=node.getFirstChild();
                continue;
            }else if(name.equals(LDMLConstants.CURRENCY_DATA)){
                res = parseCurrencyData(node, xpath);
            
            }else if(name.indexOf("icu:")>-1|| name.indexOf("openOffice:")>-1){
                //TODO: these are specials .. ignore for now ... figure out
                // what to do later
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    table.first = res;
                    current = findLast(res);
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(savedLength,xpath.length());
        }

        return table;
    }
    private ICUResourceWriter.Resource parseCurrencyFraction(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        table.name = (String) keyNameMap.get(root.getNodeName());
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            getXPath(node, xpath);
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name = name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.INFO)){
                ICUResourceWriter.ResourceIntVector vector = new ICUResourceWriter.ResourceIntVector();
                vector.name = LDMLUtilities.getAttributeValue(node, LDMLConstants.ISO_4217);
                ICUResourceWriter.ResourceInt zero = new ICUResourceWriter.ResourceInt();
                ICUResourceWriter.ResourceInt one = new ICUResourceWriter.ResourceInt();
                zero.val = LDMLUtilities.getAttributeValue(node, LDMLConstants.DIGITS);
                one.val = LDMLUtilities.getAttributeValue(node, LDMLConstants.ROUNDING);
                vector.first = zero;
                zero.next = one;
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;
    }
    private ICUResourceWriter.Resource parseCurrencyRegion(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        table.name = (String) keyNameMap.get(root.getNodeName());
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            getXPath(node, xpath);
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name = name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.REGION)){
                ICUResourceWriter.ResourceIntVector vector = new ICUResourceWriter.ResourceIntVector();
                vector.name = LDMLUtilities.getAttributeValue(node, LDMLConstants.ISO_3166);
                ICUResourceWriter.ResourceInt zero = new ICUResourceWriter.ResourceInt();
                ICUResourceWriter.ResourceInt one = new ICUResourceWriter.ResourceInt();
                zero.val = LDMLUtilities.getAttributeValue(node, LDMLConstants.DIGITS);
                one.val = LDMLUtilities.getAttributeValue(node, LDMLConstants.ROUNDING);
                vector.first = zero;
                zero.next = one;
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;
    }
    private ICUResourceWriter.Resource parseCurrencyData(Node root, StringBuffer xpath){
        ICUResourceWriter.Resource first = null;
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            getXPath(node, xpath);
            if(name.equals(LDMLConstants.REGION)){
               res = parseCurrencyRegion(node, xpath);
            }else if(name.equals(LDMLConstants.FRACTIONS)){
                res = parseCurrencyFraction(node, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = first = res;
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        return first;
    }
    private ICUResourceWriter.Resource parseBundle(Node root){
        ICUResourceWriter.ResourceTable table = null;
        ICUResourceWriter.Resource current = null;
        StringBuffer xpath = new StringBuffer();
        xpath.append("//ldml");
        int savedLength = xpath.length();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
             	continue;
            }
    		String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.ALIAS)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = "\"%%ALIAS\"";
                str.val  = LDMLUtilities.getAttributeValue(node, LDMLConstants.SOURCE);
                res = str;
                table.first = current = null;
            }else if(name.equals(LDMLConstants.IDENTITY)){
            	table = (ICUResourceWriter.ResourceTable) parseIdentity(node, xpath);
                current = findLast(table.first);
                continue;
            }else if(name.equals(LDMLConstants.LDML) ){
                if(LDMLUtilities.isNodeDraft(node) && writeDraft==false){
                    System.err.println("WARNING: The LDML file "+sourceDir+File.separator+locName+".xml is marked draft! Not producing ICU file. ");
                    System.exit(-1);
                }
                node=node.getFirstChild();
                continue;
            }else if (name.equals(LDMLConstants.SPECIAL)){
                /*
                 * IGNORE SPECIALS
                 * FOR NOW
                 */
                node=node.getFirstChild();
                continue;
            }else if(name.equals(LDMLConstants.LDN)){
                res = parseLocaleDisplayNames(node, xpath);
            }else if(name.equals(LDMLConstants.LAYOUT)){
                //TODO res = parseLayout(node, xpath);
            }else if(name.equals(LDMLConstants.CHARACTERS)){
                res = parseCharacters(node, xpath);
            }else if(name.equals(LDMLConstants.DELIMITERS)){
                res = parseDelimiters(node, xpath);
            }else if(name.equals(LDMLConstants.MEASUREMENT)){
                res = parseMeasurement(node, xpath);
            }else if(name.equals(LDMLConstants.DATES)){
                res = parseDates(node, xpath);
            }else if(name.equals(LDMLConstants.NUMBERS)){
                res = parseNumbers(node, xpath);
            }else if(name.equals(LDMLConstants.COLLATIONS)){
                if(locName.equals("root")){
                    ICUResourceWriter.ResourceInclude include = new ICUResourceWriter.ResourceInclude();
                    include.name="\"%%UCARULES\"";
                    include.val = "../unidata/UCARules.txt";
                    res = include;
                    include.next = parseCollations(node, xpath);
                }else{
                    res = parseCollations(node, xpath);
                }
            }else if(name.equals(LDMLConstants.POSIX)){
                res = parsePosix(node, xpath);
            }else if(name.indexOf("icu:")>-1|| name.indexOf("openOffice:")>-1){
                //TODO: these are specials .. ignore for now ... figure out
                // what to do later
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    table.first = res;
                    current = findLast(res);
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(savedLength,xpath.length());
        }
        // now fetch the specials and append to the real bundle
        if(specialsDir!=null && ULocale.getCountry(locName).equals("")){
            if(specialsDoc == null) {
                System.err.println("Could not open special bundle - no specials written.");
            } else {
                if(table.comment == null) {
                    table.comment = "";
                }
                ICUResourceWriter.Resource res = parseSpecials(specialsDoc);
                table.comment = table.comment + " ICU <specials> source: " + specialsDir + File.separator + locName + ".xml";
                if(res!=null){
                    if(current == null){
                        table.first = res;
                        current = findLast(res);
                    }else{
                        current.next = res;
                        current = findLast(res);
                    }
                    res = null;
                }
            }
        }
        return table;
    }
    private ICUResourceWriter.Resource findLast(ICUResourceWriter.Resource res){
        ICUResourceWriter.Resource current = res;
        while(current!=null){
            if(current.next==null){
                return current;
            }
            current = current.next;
        }
        return current;
    }
    
    private ICUResourceWriter.Resource parseAliasResource(Node node, StringBuffer xpath){
      
        try{
            if(node!=null){
                ICUResourceWriter.ResourceAlias alias = new ICUResourceWriter.ResourceAlias();
                String val = LDMLUtilities.convertXPath2ICU(node, null, xpath);
                alias.val = val;
                alias.name = node.getParentNode().getNodeName();
                return alias;
            }
        }catch(TransformerException ex){
            System.err.println("Could not compile XPATH for"+
                               " source:  " + LDMLUtilities.getAttributeValue(node, LDMLConstants.SOURCE)+
                               " path: " + LDMLUtilities.getAttributeValue(node, LDMLConstants.PATH)+
                               " Node: " + node.getParentNode().getNodeName()
                              );
            ex.printStackTrace();
            System.exit(-1);
        }
            // TODO update when XPATH is integrated into LDML
        return null;
    }
      
    private void getXPath(Node node, StringBuffer xpath){
        xpath.append("/");
        xpath.append(node.getNodeName());
        LDMLUtilities.appendXPathAttribute(node,xpath);
    }
    private ICUResourceWriter.Resource parseIdentity(Node root, StringBuffer xpath){
        String localeID="", temp;
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource res = null;
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root,xpath);
        //int oldLength = xpath.length();
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            
            if(name.equals(LDMLConstants.VERSION)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.val = LDMLUtilities.getAttributeValue(node, LDMLConstants.NUMBER);
                str.name = (String)keyNameMap.get(LDMLConstants.VERSION);
                if(LDMLUtilities.isDraft(root,new StringBuffer("//ldml"))) { // x for experimental
                    str.val = "x" + str.val;
                }
                res = str;
            }else if(name.equals(LDMLConstants.LANGUAGE)|| 
                    name.equals(LDMLConstants.SCRIPT) ||
                    name.equals(LDMLConstants.TERRITORY)||
                    name.equals(LDMLConstants.VARIANT)){
            	// here we assume that language, script, territory, variant
                // are siblings are ordered. The ordering is enforced by the DTD
                temp = LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE);
                if(temp!=null && temp.length()!=0){
                    if(localeID.length()!=0){
                    	localeID += "_";
                    }
                	localeID += temp;
                }
            }else if(name.equals(LDMLConstants.GENERATION)){
                continue;
            }else if(name.equals(LDMLConstants.ALIAS)){
                 res = parseAliasResource(node, xpath);
                 res.name = table.name;
                 return res;
            }else{
               System.err.println("Unknown element found: "+name);
               System.exit(-1);
            }
            if(res!=null){
                if(current==null ){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
           // xpath.delete(oldLength, xpath.length());  
        }
        if(localeID.length()==0){
        	localeID="root";
        }
        table.name = localeID;
        xpath.delete(savedLength, xpath.length());
        return table;
    }

    private static final String[] registeredKeys = new String[]{
    		"collation",
            "calendar",
            "currency"
    };
    private ICUResourceWriter.Resource parseLocaleDisplayNames(Node root, StringBuffer xpath){
        ICUResourceWriter.Resource first = null;
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root,xpath);
        int oldLength = xpath.length();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.LANGUAGES)   || name.equals(LDMLConstants.SCRIPTS) || 
               name.equals(LDMLConstants.TERRITORIES) || name.equals(LDMLConstants.KEYS) || 
               name.equals(LDMLConstants.VARIANTS)){
                
            	res = parseList(node, xpath);
            }else if(name.equals(LDMLConstants.TYPES)){
                res = parseDisplayTypes(node, xpath);
            }else if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
            }else{
                 System.err.println("Unknown element found: "+name);
                 System.exit(-1);
            }
            if(res!=null){
                if(current==null ){
                    current = first = res;   
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }    
        xpath.delete(savedLength, xpath.length());
        return first;
    }
    
    private ICUResourceWriter.Resource parseDisplayTypes(Node root, StringBuffer xpath){
        StringBuffer myXpath = new StringBuffer();
        myXpath.append("//ldml/localeDisplayNames/types/type[@key='");
        int savedLength = myXpath.length();
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = (String)keyNameMap.get(LDMLConstants.TYPES);
        ICUResourceWriter.ResourceTable current = null;
        for(int i=0; i<registeredKeys.length; i++){
            myXpath.append(registeredKeys[i]);
            myXpath.append("']");
        	NodeList list = LDMLUtilities.getNodeList(root.getOwnerDocument(), myXpath.toString());
            if(list.getLength()!=0){
                ICUResourceWriter.ResourceTable subTable = new ICUResourceWriter.ResourceTable();
                subTable.name = registeredKeys[i];
                if(table.first==null){
                    table.first = current = subTable;
                }else{
                    current.next = subTable;
                    current = (ICUResourceWriter.ResourceTable)current.next;
                }
                ICUResourceWriter.ResourceString currentString = null;
                for(int j=0; j<list.getLength(); j++){
                    Node item = list.item(j);
                    String type = LDMLUtilities.getAttributeValue(item, LDMLConstants.TYPE);
                    String value = LDMLUtilities.getNodeValue(item);
                    
                    String altVal = LDMLUtilities.getAttributeValue(item, LDMLConstants.ALT);
                    // the alt atrribute is set .. so ignore the resource
                    if(altVal!=null){
                        continue;
                    }
                    //TODO now check if this string is draft
                    
                    ICUResourceWriter.ResourceString string = new ICUResourceWriter.ResourceString();
                    string.name = type;
                    string.val  = value;
                    if(j==0){
                        subTable.first = currentString = string;
                    }else{
                        currentString.next = string;
                        currentString = (ICUResourceWriter.ResourceString)currentString.next;
                    }
                }
            }
            myXpath.delete(savedLength, myXpath.length());
        }
        if(table.first!=null){
            return table;
        }
        return null;
    }
    private ICUResourceWriter.Resource parseList(Node root, StringBuffer xpath){
    	ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name=(String) keyNameMap.get(root.getNodeName());
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        
        Node alias = LDMLUtilities.getNode(root,"alias", null, null);
        if(alias!=null){
            ICUResourceWriter.Resource res =  parseAliasResource(alias,xpath);
            res.name = table.name;
            return res;
        }
        
        for(Node node = root.getFirstChild(); node!=null; node=node.getNextSibling()){
        	if(node.getNodeType()!=Node.ELEMENT_NODE){
        		continue;
            }
            getXPath(node, xpath);
            String altVal = LDMLUtilities.getAttributeValue(node, LDMLConstants.ALT);
            // the alt atrribute is set .. so ignore the resource
            if(altVal!=null){
                continue;
            }
            if(current==null){
            	current = table.first = new ICUResourceWriter.ResourceString();
            }else{
            	current.next = new ICUResourceWriter.ResourceString();
                current = current.next;
            }
            current.name = LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE);

            ((ICUResourceWriter.ResourceString)current).val  = LDMLUtilities.getNodeValue(node);
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;
    }
    private ICUResourceWriter.Resource parseArray(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceArray array = new ICUResourceWriter.ResourceArray();
        array.name=(String) keyNameMap.get(root.getNodeName());
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        for(Node node = root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            getXPath(node, xpath);
            String altVal = LDMLUtilities.getAttributeValue(node, LDMLConstants.ALT);
            // the alt atrribute is set .. so ignore the resource
            if(altVal!=null){
                continue;
            }
            if(current==null){
                current = array.first = new ICUResourceWriter.ResourceString();
            }else{
                current.next = new ICUResourceWriter.ResourceString();
                current = current.next;
            }
            //current.name = LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE);

            ((ICUResourceWriter.ResourceString)current).val  = LDMLUtilities.getNodeValue(node);
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(array.first!=null){
            return array;
        }
        return null;
    }


    private static final String ICU_SCRIPTS = "icu:scripts";
    private static final String ICU_SCRIPT = "icu:script";
    private ICUResourceWriter.Resource parseCharacters(Node root, StringBuffer xpath){
        ICUResourceWriter.Resource current = null, first=null;
        int savedLength = xpath.length();
        getXPath(root,xpath);
        int oldLength = xpath.length();
        for(Node node = root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.EXEMPLAR_CHARACTERS)){
                res = parseStringResource(node);
                res.name = (String) keyNameMap.get(LDMLConstants.EXEMPLAR_CHARACTERS);
            }else if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
            }else if(name.equals(LDMLConstants.MAPPING)){
                //TODO: Currently we dont have a way to represent this data in ICU!
            }else if(name.equals(LDMLConstants.SPECIAL)){
                res = parseSpecialElements(node, xpath);
            }else{
                 System.err.println("Unknown element found: "+name);
                 System.exit(-1);
            }
            if(res!=null){
                if(current==null ){
                    first = res;   
                    current = findLast(res);
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        return first;
    }
    private ICUResourceWriter.Resource parseStringResource(Node node){
        ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
        str.val = LDMLUtilities.getNodeValue(node);
        str.name = node.getNodeName();
        return str;
    }

    private ICUResourceWriter.Resource parseDelimiters(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = root.getNodeName();
        getXPath(root,xpath);
        ICUResourceWriter.Resource res = parseDelimiter(root, xpath);
        if(res!=null){
            table.first = res;
            return table;
        }
        return null;
    }
    private ICUResourceWriter.Resource parseDelimiter(Node root, StringBuffer xpath){
        ICUResourceWriter.Resource current = null,first=null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        
        for(Node node = root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.QS) || name.equals(LDMLConstants.QE)||
               name.equals(LDMLConstants.AQS)|| name.equals(LDMLConstants.AQE)){
                res = parseStringResource(node);
            }else if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
            }else if(name.equals(LDMLConstants.MAPPING)){
                //TODO: Currently we dont have a way to represent this data in ICU!
            }else{
                 System.err.println("Unknown element found: "+name);
                 System.exit(-1);
            }
            if(res!=null){
                if(current==null ){
                    current = first = res;   
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength,xpath.length());
        return first;
    }

    
    private ICUResourceWriter.Resource parsePaperSize(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceIntVector vector = new ICUResourceWriter.ResourceIntVector();
        vector.name = (String) keyNameMap.get(root.getNodeName());
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        for(Node node = root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            // here we assume that the DTD enforces the correct order
            // of elements
            if(name.equals(LDMLConstants.HEIGHT)||name.equals(LDMLConstants.WIDTH)){
                ICUResourceWriter.ResourceInt resint = new ICUResourceWriter.ResourceInt();
                resint.val = LDMLUtilities.getNodeValue(node);
                res = resint;
            }else if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                //We know that paperSize element can only contain either alias or (height and width)
                return res; 
            }else{
                System.err.println("Unknown element found: "+name);
                System.exit(-1);              
            }
            if(res != null){
                if(current == null){
                    current = vector.first = (ICUResourceWriter.ResourceInt) res;
                }else{
                    current.next = res;
                    current = current.next;
                }
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        return vector;
    }
    private ICUResourceWriter.Resource parseMeasurement (Node root, StringBuffer xpath){
        ICUResourceWriter.Resource current = null,first=null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        for(Node node = root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.MS)){
                String altVal = LDMLUtilities.getAttributeValue(node, LDMLConstants.ALT);
                // the alt atrribute is set .. so ignore the resource
                if(altVal!=null){
                    continue;
                }
                ICUResourceWriter.ResourceInt resint = new ICUResourceWriter.ResourceInt();
                String sys = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                if(sys.equals("US")){
                    resint.val = "1";
                }else{
                    resint.val = "0";
                }
                resint.name = (String) keyNameMap.get(LDMLConstants.MS);
                res = resint;
            }else if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node,xpath);
            }else if(name.equals(LDMLConstants.PAPER_SIZE)){
                res = parsePaperSize(node, xpath);
            }else{
                 System.err.println("Unknown element found: "+name);
                 System.exit(-1);
            }
            if(res!=null){
                if(current==null ){
                    current = first = res;   
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        return first;
    }

    
    private ICUResourceWriter.Resource parseDates(Node root, StringBuffer xpath){
        ICUResourceWriter.Resource first = null;
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            if(name.equals(LDMLConstants.ALIAS)){
                //dont compute xpath
                res = parseAliasResource(node,xpath);
            }else if(name.equals(LDMLConstants.DEFAULT) ){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.LPC)){
                getXPath(node, xpath);
                if(xpath.indexOf(LDMLConstants.ALT)<0){
                    ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                    str.name = (String) keyNameMap.get(name);
                    str.val = LDMLUtilities.getNodeValue(node);
                    res = str;
                }
            }else if(name.equals(LDMLConstants.CALENDARS)){
                res = parseCalendars(node, xpath);
            }else if(name.equals(LDMLConstants.TZN)){
                res = parseTimeZoneNames(node, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = first = res;
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        return first;
    }

    private ICUResourceWriter.Resource parseCalendars(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        table.name = LDMLConstants.CALENDAR;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name =table.name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.CALENDAR)){
                res = parseCalendar(node, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;
    }
    
    private ICUResourceWriter.Resource parseTimeZoneNames(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceArray array = new ICUResourceWriter.ResourceArray();
        ICUResourceWriter.Resource current = null;
        array.name = (String)keyNameMap.get(root.getNodeName());
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node,xpath);
                res.name =array.name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.ZONE)){
                res = parseZone(node, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = array.first = res;
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(array.first!=null){
            return array;
        }
        return null;
    }
    
    
    private ICUResourceWriter.Resource parseZone(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceArray array = new ICUResourceWriter.ResourceArray();
        //ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        
        ICUResourceWriter.ResourceString type = new ICUResourceWriter.ResourceString();
        
        ICUResourceWriter.ResourceString ss = new ICUResourceWriter.ResourceString();
        ICUResourceWriter.ResourceString sd = new ICUResourceWriter.ResourceString();

        ICUResourceWriter.ResourceString ls = new ICUResourceWriter.ResourceString();
        ICUResourceWriter.ResourceString ld = new ICUResourceWriter.ResourceString();

        ICUResourceWriter.ResourceString exemplarCity = new ICUResourceWriter.ResourceString();
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.SHORT)){
                /* get information about long */
                Node ssn = LDMLUtilities.getNode(node, LDMLConstants.STANDARD, fullyResolvedDoc, xpath.toString());
                Node sdn = LDMLUtilities.getNode(node, LDMLConstants.DAYLIGHT, fullyResolvedDoc, xpath.toString());
                if(ssn==null||sdn==null){
                    System.err.println("Could not get timeZone string for " + xpath.toString());
                    System.exit(-1);
                }
                ss.val = LDMLUtilities.getNodeValue(ssn);
                sd.val = LDMLUtilities.getNodeValue(sdn);
                // ok the nodes are availble but
                // the values are null .. so just set the values to empty strings
                if(ss.val==null){
                    ss.val = "";
                }
                if(sd.val==null){
                    sd.val = "";
                }
            }else if(name.equals(LDMLConstants.LONG)){
                /* get information about long */
                Node lsn = LDMLUtilities.getNode(node, LDMLConstants.STANDARD, fullyResolvedDoc, xpath.toString());
                Node ldn = LDMLUtilities.getNode(node, LDMLConstants.DAYLIGHT, fullyResolvedDoc, xpath.toString());
                if(lsn==null||ldn==null){
                    System.err.println("Could not get timeZone string for " + xpath.toString());
                    System.exit(-1);
                }
                ls.val = LDMLUtilities.getNodeValue(lsn);
                ld.val = LDMLUtilities.getNodeValue(ldn);
                // ok the nodes are availble but
                // the values are null .. so just set the values to empty strings
                if(ls.val==null){
                    ls.val = "";
                }
                if(ld.val==null){
                    ld.val = "";
                }
            }else if(name.equals(LDMLConstants.EXEMPLAR_CITY)){
                exemplarCity.val = LDMLUtilities.getNodeValue(node);
                if(exemplarCity.val==null){
                    exemplarCity.val = "";
                }
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            xpath.delete(oldLength, xpath.length());
        }
        if(exemplarCity.val==null){
            Node ecn = LDMLUtilities.getNode(root, LDMLConstants.EXEMPLAR_CITY, fullyResolvedDoc, xpath.toString());
            //TODO: Fix this when zoneStrings format c
            if(ecn!=null){
                exemplarCity.val = LDMLUtilities.getNodeValue(ecn);
            }
        }
        
        type.val = LDMLUtilities.getAttributeValue(root, LDMLConstants.TYPE);
        if(type.val==null){
            type.val="";
        }
        /* assemble the array */
        if(type.val!=null && ls.val!=null && ss.val!=null && ld.val!=null && sd.val!=null){
            array.first = type;     /* [0] == type */ 
            
            type.next = ls;         /* [1] == long name for Standard */
            ls.next = ss;           /* [2] == short name for standard */
            ss.next = ld;           /* [3] == long name for daylight */
            ld.next = sd;           /* [4] == short name for standard */
            if(exemplarCity.val!=null){
                sd.next = exemplarCity; /* [5] == exemplarCity */
            }
        }
        xpath.delete(savedLength, xpath.length());
        if(array.first!=null){
            return array;
        }
        return null;
    }
    
    private ICUResourceWriter.Resource parseShortLong(Node root, StringBuffer xpath){
        int savedLength = xpath.length();
        getXPath(root, xpath);
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        Node sn = LDMLUtilities.getNode(root, LDMLConstants.STANDARD, fullyResolvedDoc, xpath.toString());
        Node dn = LDMLUtilities.getNode(root, LDMLConstants.DAYLIGHT, fullyResolvedDoc, xpath.toString());
        if(sn==null||dn==null){
            System.err.println("Could not get timeZone string for " + xpath.toString());
            System.exit(-1);
        }
        ICUResourceWriter.ResourceString ss = new ICUResourceWriter.ResourceString();
        ICUResourceWriter.ResourceString ds = new ICUResourceWriter.ResourceString();
        ss.val = LDMLUtilities.getNodeValue(sn);
        ds.val = LDMLUtilities.getNodeValue(dn);
        xpath.delete(savedLength, xpath.length());
        ss.next = ds;
        return ss;
    }
      
    private ICUResourceWriter.Resource parseCalendar(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        boolean writtenAmPm = false;
        boolean writtenDTF = false;
        table.name = LDMLUtilities.getAttributeValue(root,LDMLConstants.TYPE);
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node,xpath);
                res.name =table.name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.MONTHS)|| name.equals(LDMLConstants.DAYS)){
                res = parseMonthsAndDays(node, xpath);
            }else if(name.equals(LDMLConstants.WEEK)){
                ICUResourceWriter.Resource temp = parseWeek(node, xpath);
                if(temp!=null){
                    res = temp;
                }
            }else if(name.equals(LDMLConstants.AM)|| name.equals(LDMLConstants.PM)){
                //TODO: figure out the tricky parts .. basically get the missing element from
                // fully resolved locale!
                if(writtenAmPm==false){
                    res = parseAmPm(node, xpath);
                    writtenAmPm = true;
                }
            }else if(name.equals(LDMLConstants.ERAS)){
                res = parseEras(node, xpath);
            }else if(name.equals(LDMLConstants.DATE_FORMATS)||name.equals(LDMLConstants.TIME_FORMATS)|| name.equals(LDMLConstants.DATE_TIME_FORMATS)){
                // TODO
                if(writtenDTF==false){
                    res = parseDTF(node, xpath);
                    writtenDTF = true;
                }
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
//            table.sort();
            return table;
        }
        return null;
    }
    
    private ICUResourceWriter.Resource parseMonthsAndDays(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        table.name = (String)keyNameMap.get(root.getNodeName());
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name=table.name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.MONTH_CONTEXT)|| name.equals(LDMLConstants.DAY_CONTEXT)){
                res = parseContext(node, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;
    }
    private ICUResourceWriter.Resource parseContext(Node root, StringBuffer xpath ){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        table.name = LDMLUtilities.getAttributeValue(root,LDMLConstants.TYPE);
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        String resName = root.getNodeName();
        resName = resName.substring(0, resName.lastIndexOf("Context"));
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name = table.name;
                return res; // an alias if for the resource
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(resName+"Width")){
                res = parseWidth(node, resName, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;
    }
    
    private String getDayNumberAsString(String type){
        if(type.equals("sun")){
            return "1";
        }else if(type.equals("mon")){
            return "2";
        }else if(type.equals("tue")){
            return "3";
        }else if(type.equals("wed")){
            return "4";
        }else if(type.equals("thu")){
            return "5";
        }else if(type.equals("fri")){
            return "6";
        }else if(type.equals("sat")){
            return "7";
        }else{
            throw new IllegalArgumentException("Unknown type: "+type);
        }
    }
    
    
    private ICUResourceWriter.Resource parseWidth(Node root, String resName, StringBuffer xpath){
        ICUResourceWriter.ResourceArray array = new ICUResourceWriter.ResourceArray();
        ICUResourceWriter.Resource current = null;
        array.name = LDMLUtilities.getAttributeValue(root,LDMLConstants.TYPE);

        int savedLength = xpath.length();
        getXPath(root, xpath);
        //int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        Node alias = LDMLUtilities.getNode(root,"alias", null, null);
        if(alias!=null){
            ICUResourceWriter.Resource res =  parseAliasResource(alias,xpath);
            res.name = LDMLUtilities.getAttributeValue(root, LDMLConstants.TYPE);
            return res;
        }
        
        HashMap map = getElementsMap(root);
        if((resName.equals(LDMLConstants.DAY) && map.size()<7) ||
            (resName.equals(LDMLConstants.MONTH)&& map.size()<12)){
            root = LDMLUtilities.getNode(fullyResolvedDoc,xpath.toString() );
            map = getElementsMap(root);
        }
        if(map.size()>0){
            for(int i=0; i<map.size(); i++){
               String key = Integer.toString(i);
               ICUResourceWriter.ResourceString res = new ICUResourceWriter.ResourceString();
               res.val = (String)map.get(key);
               // array of unnamed strings
               if(current == null){
                   current = array.first = res;
               }else{
                   current.next = res;
                   current = current.next;
               }
            }
        }
        
        // parse the default node
        Node def = LDMLUtilities.getNode(root,LDMLConstants.DEFAULT, null, null);
        if(def!=null){
            ICUResourceWriter.ResourceString res = new ICUResourceWriter.ResourceString();
            res.val = LDMLUtilities.getAttributeValue(def, LDMLConstants.TYPE);
            res.name = LDMLConstants.DEFAULT;
            if(current == null){
                current = array.first = res;
            }else{
                current.next = res;
                current = current.next;
            }
        }
        if(array.first!=null){
            return array;
        }
        xpath.delete(savedLength, xpath.length());
        return null;
    }
    private HashMap getElementsMap(Node root){
        HashMap map = new HashMap();
        // first create the hash map;
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            String val = LDMLUtilities.getNodeValue(node);
            String type = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
            String alt = LDMLUtilities.getAttributeValue(node,LDMLConstants.ALT);
            if(alt!=null){
                // ignore elements with alt attribute set
                continue;
            }
            if(name.equals(LDMLConstants.DAY)){
                map.put(LDMLUtilities.getDayIndexAsString(type), val);
            }else if(name.equals(LDMLConstants.MONTH)){
                map.put(LDMLUtilities.getMonthIndexAsString(type), val);
            }else if( name.equals(LDMLConstants.ERA)){
                map.put(type, val);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
        }
        return map;
    }

    
    private ICUResourceWriter.Resource parseWeek(Node root, StringBuffer xpath){
        ICUResourceWriter.Resource dte = parseDTE(root, xpath);
        ICUResourceWriter.Resource wkend = parseWeekend(root, xpath);
        if(dte!=null){
            dte.next = wkend;
            return dte;
        }
        return wkend;
    }
    private int getMillis(String time){
       String[] strings = time.split(":"); // time is in hh:mm format
       int hours = Integer.parseInt(strings[0]);
       int minutes = Integer.parseInt(strings[1]);
       return  (hours * 60  + minutes ) * 60 * 1000;
    }
    private ICUResourceWriter.Resource parseWeekend(Node root, StringBuffer xpath){
        Node wkendStart = LDMLUtilities.getNode(root, LDMLConstants.WENDSTART, fullyResolvedDoc, xpath.toString());
        Node wkendEnd = LDMLUtilities.getNode(root, LDMLConstants.WENDEND, fullyResolvedDoc, xpath.toString());
        ICUResourceWriter.ResourceIntVector wkend = null;
        
        if(wkendStart!=null && wkendEnd!=null){
            try{
                wkend =  new ICUResourceWriter.ResourceIntVector();
                wkend.name = LDMLConstants.WEEKEND;
                ICUResourceWriter.ResourceInt startday = new ICUResourceWriter.ResourceInt();
                startday.val = getDayNumberAsString(LDMLUtilities.getAttributeValue(wkendStart, LDMLConstants.DAY));
                ICUResourceWriter.ResourceInt starttime = new ICUResourceWriter.ResourceInt();
                starttime.val = Integer.toString(getMillis(LDMLUtilities.getAttributeValue(wkendStart, LDMLConstants.TIME)));
                ICUResourceWriter.ResourceInt endday = new ICUResourceWriter.ResourceInt();
                endday.val = getDayNumberAsString(LDMLUtilities.getAttributeValue(wkendEnd, LDMLConstants.DAY));
                ICUResourceWriter.ResourceInt endtime = new ICUResourceWriter.ResourceInt();
                endtime.val = Integer.toString(getMillis(LDMLUtilities.getAttributeValue(wkendEnd, LDMLConstants.TIME)));
                wkend.first = startday;
                startday.next = starttime;
                starttime.next = endday;
                endday.next = endtime;
            }catch(NullPointerException ex){
                throw new RuntimeException(ex);
            }
        }
 
        return wkend; 
    }
    private ICUResourceWriter.Resource parseDTE(Node root, StringBuffer xpath){
        Node minDays = LDMLUtilities.getNode(root, LDMLConstants.MINDAYS, fullyResolvedDoc, xpath.toString());
        Node firstDay = LDMLUtilities.getNode(root, LDMLConstants.FIRSTDAY, fullyResolvedDoc, xpath.toString());
        ICUResourceWriter.ResourceIntVector dte = null;
        if(minDays!=null && firstDay!=null){
            dte =  new ICUResourceWriter.ResourceIntVector();
            ICUResourceWriter.ResourceInt int1 = new ICUResourceWriter.ResourceInt();
            int1.val = getDayNumberAsString(LDMLUtilities.getAttributeValue(firstDay, LDMLConstants.DAY));
            ICUResourceWriter.ResourceInt int2 = new ICUResourceWriter.ResourceInt();
            int2.val = LDMLUtilities.getAttributeValue(minDays, LDMLConstants.COUNT); 
            
            dte.name = DTE;
            dte.first = int1;
            int1.next = int2;
        }
        if((minDays==null && firstDay!=null) || minDays!=null && firstDay==null){
            throw new RuntimeException("Could not find "+minDays+" or "+firstDay +" from fullyResolved locale!!");
        }
        return dte;
    }
    
 
    private ICUResourceWriter.Resource parseEras(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        table.name = LDMLConstants.ERAS;
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name =table.name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.ERAABBR)){
                res = parseEra(node, xpath, LDMLConstants.ABBREVIATED);
            }else if( name.equals(LDMLConstants.ERANAMES)){
                res = parseEra(node, xpath, LDMLConstants.WIDE);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;
              
    }
    private ICUResourceWriter.Resource parseEra( Node root, StringBuffer xpath, String name){
        ICUResourceWriter.ResourceArray array = new ICUResourceWriter.ResourceArray();
        ICUResourceWriter.Resource current = null;
        array.name = name;

        int savedLength = xpath.length();
        getXPath(root, xpath);
       // int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        
        HashMap map = getElementsMap(root);

        if(map.size()>0){
            for(int i=0; i<map.size(); i++){
               String key = Integer.toString(i);
               ICUResourceWriter.ResourceString res = new ICUResourceWriter.ResourceString();
               res.val = (String)map.get(key);
               //TODO: fix this!!
               if(res.val==null){
                   continue;
               }
               // array of unnamed strings
               if(current == null){
                   current = array.first = res;
               }else{
                   current.next = res;
                   current = current.next;
               }
            }
        }
        xpath.delete(savedLength,xpath.length());
        if(array.first!=null){
            return array;
        }
        return null;
   
    }
    private boolean isDraft(Node node, StringBuffer xpath){
        if(LDMLUtilities.isNodeDraft(node)){
            return true;
        }
        if(LDMLUtilities.isDraft(fullyResolvedDoc, xpath)){
            return true;
        }
        return false;
    }
    /*
    private boolean isAlternate(Node node){
        String alt = LDMLUtilities.getAttributeValue(node, LDMLConstants.ALT);
        if(alt!=null){
            return true;
        }
        return false;
    }
    */
    private Node getVettedNode(NodeList list, StringBuffer xpath){
        // A vetted node is one which is not draft and does not have alternate
        // attribute set
        Node node =null;
        for(int i =0; i<list.getLength(); i++){
            node = list.item(i);
            if(!isDraft(node, xpath)&& !isAlternate(node)){
                return node;
            }
        }
        return null;
    }
    private ICUResourceWriter.Resource parseAmPm(Node root, StringBuffer xpath){
        Node parent =root.getParentNode();
        Node amNode = null;
        NodeList list = LDMLUtilities.getNodeList(parent, LDMLConstants.AM, fullyResolvedDoc, xpath.toString());
        int oldLength=xpath.length();
        if(list!=null){
            amNode = getVettedNode(list,xpath.append("/"+LDMLConstants.AM));
        }
        xpath.setLength(oldLength);
        Node pmNode = null;
        list = LDMLUtilities.getNodeList(parent, LDMLConstants.PM, fullyResolvedDoc, xpath.toString());
        if(list!=null){
            pmNode = getVettedNode(list, xpath.append("/"+LDMLConstants.PM));
        }
        ICUResourceWriter.ResourceArray arr = null;
        if(amNode!=null && pmNode!= null){
            arr = new ICUResourceWriter.ResourceArray();
            arr.name = AM_PM_MARKERS;
            ICUResourceWriter.ResourceString am = new ICUResourceWriter.ResourceString();
            ICUResourceWriter.ResourceString pm = new ICUResourceWriter.ResourceString();
            am.val = LDMLUtilities.getNodeValue(amNode);
            pm.val = LDMLUtilities.getNodeValue(pmNode);
            arr.first = am;
            am.next = pm;
        }
        if((amNode==null && pmNode!=null) || amNode!=null && pmNode==null){
            throw new RuntimeException("Could not find "+amNode+" or "+pmNode +" from fullyResolved locale!!");
        }
        return arr;
    }
    
    
    private ICUResourceWriter.Resource parseDTF(Node root, StringBuffer xpath){
        // TODO change the ICU format to reflect LDML format
        /*
         * The prefered ICU format would be
         * timeFormats{
         *      default{}
         *      full{}
         *      long{}
         *      medium{}
         *      short{}
         *      ....
         * }
         * dateFormats{
         *      default{}
         *      full{}
         *      long{}
         *      medium{}
         *      short{}
         *      .....
         * }
         * dateTimeFormats{
         *      standard{}
         *      ....
         * }   
         */
        
        // here we dont add stuff to XPATH since we are querying the parent 
        // with the hardcoded XPATHS!
        
        //TODO figure out what to do for alias
        Node parent = root.getParentNode();
        ArrayList list = new ArrayList();
        list.add(LDMLUtilities.getNode(parent, "timeFormats/timeFormatLength[@type='full']/timeFormat[@type='standard']/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "timeFormats/timeFormatLength[@type='long']/timeFormat[@type='standard']/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "timeFormats/timeFormatLength[@type='medium']/timeFormat[@type='standard']/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "timeFormats/timeFormatLength[@type='short']/timeFormat[@type='standard']/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "dateFormats/dateFormatLength[@type='full']/dateFormat[@type='standard']/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "dateFormats/dateFormatLength[@type='long']/dateFormat[@type='standard']/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "dateFormats/dateFormatLength[@type='medium']/dateFormat[@type='standard']/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "dateFormats/dateFormatLength[@type='short']/dateFormat[@type='standard']/pattern", fullyResolvedDoc, xpath.toString()));
        //TODO guard this against possible failure 
        list.add(LDMLUtilities.getNode(parent, "dateTimeFormats/dateTimeFormatLength/dateTimeFormat/pattern", fullyResolvedDoc, xpath.toString()));
        
        if(list.size()<9){
            throw new RuntimeException("Did not get expected output for Date and Time patterns!!");
        }
        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        arr.name = DTP;
        ICUResourceWriter.Resource current = null;
        for(int i= 0; i<list.size(); i++){
            ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
            Node temp = (Node)list.get(i);
            if(temp==null){
                throw new RuntimeException("Did not get expected output for Date and Time patterns!!");
            }
            str.val = LDMLUtilities.getNodeValue(temp);
            if(str.val!=null){
                if(current==null){
                    current = arr.first = str;
                }else{
                    current.next = str;
                    current = current.next;
                }
            }else{
                throw new RuntimeException("the node value for Date and Time patterns is null!!");
            }     
        }
        
        if(arr.first!=null){
            return arr;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseNumbers(Node root, StringBuffer xpath){
        ICUResourceWriter.Resource current = null, first =null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        boolean writtenFormats = false;
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            getXPath(node, xpath);
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name = name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.SYMBOLS)){
                res = parseSymbols(node, xpath);
            }else if( name.equals(LDMLConstants.DECIMAL_FORMATS) || name.equals(LDMLConstants.PERCENT_FORMATS)|| 
                     name.equals(LDMLConstants.SCIENTIFIC_FORMATS)||name.equals(LDMLConstants.CURRENCY_FORMATS) ){
                if(writtenFormats==false){
                    res = parseNumberFormats(node, xpath);
                    writtenFormats = true;
                }
            }else if(name.equals(LDMLConstants.CURRENCIES)){
                res = parseCurrencies(node, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = first = res;
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(first!=null){
            return first;
        }
        return null;
    }
        
    private ICUResourceWriter.Resource parseSymbols(Node root, StringBuffer xpath){
        int savedLength = xpath.length();
        getXPath(root, xpath);
        //int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        //TODO figure out what to do for alias
        ArrayList list = new ArrayList();
        list.add(LDMLUtilities.getNode(root, "decimal", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "group", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "list", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "percentSign", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "nativeZeroDigit", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "patternDigit", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "minusSign", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "exponential", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "perMille", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "infinity", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "nan", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(root, "plusSign", fullyResolvedDoc, xpath.toString()));
        
        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        arr.name = NUMBER_ELEMENTS;
        ICUResourceWriter.Resource current = null;
        for(int i= 0; i<list.size(); i++){
            ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
            Node temp = (Node)list.get(i);
            if(temp==null){
                throw new RuntimeException("Did not get expected output for Date and Time patterns!!");
            }
            str.val = LDMLUtilities.getNodeValue(temp);
            if(str.val!=null){
                if(current==null){
                    current = arr.first = str;
                }else{
                    current.next = str;
                    current = current.next;
                }
            }else{
                throw new RuntimeException("the node value for Date and Time patterns is null!!");
            }     
        }

        xpath.delete(savedLength, xpath.length());
        
        if(arr.first!=null){
            return arr;
        }
        return null;
        
    }
    private ICUResourceWriter.Resource parseNumberFormats(Node root, StringBuffer xpath){
       
        //      here we dont add stuff to XPATH since we are querying the parent 
        //      with the hardcoded XPATHS!
        
        //TODO figure out what to do for alias
        Node parent = root.getParentNode();
        ArrayList list = new ArrayList();
        list.add(LDMLUtilities.getNode(parent, "decimalFormats/decimalFormatLength/decimalFormat/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "currencyFormats/currencyFormatLength/currencyFormat/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "percentFormats/percentFormatLength/percentFormat/pattern", fullyResolvedDoc, xpath.toString()));
        list.add(LDMLUtilities.getNode(parent, "scientificFormats/scientificFormatLength/scientificFormat/pattern", fullyResolvedDoc, xpath.toString()));
        
        if(list.size()<4){
            throw new RuntimeException("Did not get expected output for number patterns!!");
        }
        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        arr.name = NUMBER_PATTERNS;
        ICUResourceWriter.Resource current = null;
        for(int i= 0; i<list.size(); i++){
            ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
            Node temp = (Node)list.get(i);
            if(temp==null){
                throw new RuntimeException("Did not get expected output for number patterns!!");
            }
            str.val = LDMLUtilities.getNodeValue(temp);
            if(str.val!=null){
                if(current==null){
                    current = arr.first = str;
                }else{
                    current.next = str;
                    current = current.next;
                }
            }else{
                throw new RuntimeException("the node value for number patterns is null!!");
            }     
        }
        
        if(arr.first!=null){
            return arr;
        }
        return null;

    }
    
    private ICUResourceWriter.Resource parseCurrencies(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        table.name = (String) keyNameMap.get(root.getNodeName());
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            getXPath(node, xpath);
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name = name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.CURRENCY)){
                res = parseCurrency(node, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first = res;
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;
    }
        
    private ICUResourceWriter.Resource parseCurrency(Node root, StringBuffer xpath){
        int savedLength = xpath.length();
        getXPath(root, xpath);
        //int oldLength = xpath.length();
        if(xpath.indexOf(LDMLConstants.ALT)>-1){
            return null;
        }
        Node alias = LDMLUtilities.getNode(root, LDMLConstants.ALIAS, fullyResolvedDoc, xpath.toString());
        if(alias!=null){
            ICUResourceWriter.Resource res = parseAliasResource(alias, xpath);
            res.name = LDMLUtilities.getAttributeValue(root, LDMLConstants.TYPE);
            xpath.delete(savedLength, xpath.length());
            return res;
        }
        Node symbolNode = LDMLUtilities.getNode(root, LDMLConstants.SYMBOL , fullyResolvedDoc, xpath.toString());
        Node displayNameNode = LDMLUtilities.getNode(root, LDMLConstants.DISPLAY_NAME , fullyResolvedDoc, xpath.toString());
        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        arr.name = LDMLUtilities.getAttributeValue(root, LDMLConstants.TYPE);
        if(symbolNode==null||displayNameNode==null){
            throw new RuntimeException("Could not get dispaly name and symbol from currency resource!!");
        }
        ICUResourceWriter.ResourceString symbol = new ICUResourceWriter.ResourceString();
        symbol.val = LDMLUtilities.getNodeValue(symbolNode);
        ICUResourceWriter.ResourceString displayName = new ICUResourceWriter.ResourceString();
        displayName.val = LDMLUtilities.getNodeValue(displayNameNode);
        
        arr.first = symbol;
        symbol.next = displayName;
        
        Node patternNode = LDMLUtilities.getNode(root, LDMLConstants.PATTERN , fullyResolvedDoc, xpath.toString());
        Node decimalNode = LDMLUtilities.getNode(root, LDMLConstants.DECIMAL , fullyResolvedDoc, xpath.toString());
        Node groupNode   = LDMLUtilities.getNode(root, LDMLConstants.GROUP , fullyResolvedDoc, xpath.toString());
        if(patternNode!=null || decimalNode!=null || groupNode!=null){
            if(patternNode==null || decimalNode==null || groupNode==null){
                throw new RuntimeException("Could not get pattern or decimal or group currency resource!!");
            }
            ICUResourceWriter.ResourceArray elementsArr = new ICUResourceWriter.ResourceArray();
            
            ICUResourceWriter.ResourceString pattern = new ICUResourceWriter.ResourceString();
            pattern.val = LDMLUtilities.getNodeValue(patternNode);
            
            ICUResourceWriter.ResourceString decimal = new ICUResourceWriter.ResourceString();
            decimal.val = LDMLUtilities.getNodeValue(decimalNode);
            
            ICUResourceWriter.ResourceString group = new ICUResourceWriter.ResourceString();
            group.val = LDMLUtilities.getNodeValue(groupNode);
            
            elementsArr.first = pattern;
            pattern.next = decimal;
            decimal.next = group;
            
            displayName.next = elementsArr;
        }
        xpath.delete(savedLength, xpath.length());
        if(arr.first!=null){
            return arr;
        }
        return arr;      
    }
    
    private ICUResourceWriter.Resource parsePosix(Node root, StringBuffer xpath){ 
        ICUResourceWriter.Resource first = null;
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root,xpath);
        int oldLength = xpath.length();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.MESSAGES)){
                res = parseMessages(node, xpath);
            }else if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
            }else{
                 System.err.println("Unknown element found: "+name);
                 System.exit(-1);
            }
            if(res!=null){
                if(current==null ){
                    current = first = res;   
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }    
        xpath.delete(savedLength, xpath.length());
        return first;
    }
    
    private ICUResourceWriter.Resource parseMessages(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        int savedLength = xpath.length();
        getXPath(root,xpath);
        int oldLength = xpath.length();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.YESSTR)||name.equals(LDMLConstants.YESEXPR)||name.equals(LDMLConstants.NOSTR)||name.equals(LDMLConstants.NOEXPR)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getNodeValue(node);
                res = str;
            }else if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
            }else{
                 System.err.println("Unknown element found: "+name);
                 System.exit(-1);
            }
            if(res!=null){
                if(current==null ){
                    current = table.first = res;   
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }    
        xpath.delete(savedLength, xpath.length());
        return table.first;
    }
    
    private ICUResourceWriter.Resource parseCollations(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        table.name = root.getNodeName();
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
     
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name =table.name;
                return res;
            }else if(name.equals(LDMLConstants.DEFAULT)){
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = LDMLUtilities.getAttributeValue(node,LDMLConstants.TYPE);
                res = str;
            }else if(name.equals(LDMLConstants.COLLATION)){
                res = parseCollation(node, xpath);
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first= res;
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if(table.first!=null){
            return table;
        }
        return null;

    }
    
    private ICUResourceWriter.Resource parseCollation(Node root, StringBuffer xpath){
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        table.name = LDMLUtilities.getAttributeValue(root, LDMLConstants.TYPE);
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        StringBuffer rules = new StringBuffer();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.ALIAS)){
                res = parseAliasResource(node, xpath);
                res.name =table.name;
                return res;
            }else if(name.equals(LDMLConstants.RULES)){
                Node alias = LDMLUtilities.getNode(node, LDMLConstants.ALIAS , fullyResolvedDoc, xpath.toString());
                getXPath(node, xpath);
                if(alias!=null){
                    res = parseAliasResource(alias, xpath);
                }else{
                    rules.append( parseRules(node, xpath));
                }
            }else if(name.equals(LDMLConstants.SETTINGS)){
                //TODO
                rules.append(parseSettings(node));
            }else if(name.equals(LDMLConstants.SUPPRESS_CONTRACTIONS)){
                if(DEBUG)System.out.println("");
                
                int index = rules.length();
                rules.append("[suppressContractions");
                rules.append(LDMLUtilities.getNodeValue(node));
                rules.append("]");
                if(DEBUG) System.out.println(rules.substring(index));
            }else if(name.equals(LDMLConstants.OPTIMIZE)){
                rules.append("[optimize");
                rules.append(LDMLUtilities.getNodeValue(node));
                rules.append("]");
            }else if (name.equals(LDMLConstants.BASE)){
                //TODO Dont know what to do here
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    current = table.first= res;
                }else{
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        if(rules!=null){
            ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
            str.name = LDMLConstants.SEQUENCE;
            str.val = rules.toString();
            if(current == null){
                current = table.first= str;
            }else{
                current.next = str;
                current = current.next;
            }
            str = new ICUResourceWriter.ResourceString();
            str.name = "Version";
            Node version = LDMLUtilities.getNode(specialsDoc, xpath.append("/special").toString());
            if(version!=null){
                str.val = LDMLUtilities.getAttributeValue(version, "icu:version");
            }
            current.next = str;
            
        }

        xpath.delete(savedLength, xpath.length());
        
        if(table.first!=null){
            return table;
        }
        return null;        
    }
    private StringBuffer parseSettings(Node node){
        String strength = LDMLUtilities.getAttributeValue(node, LDMLConstants.STRENGTH);
        StringBuffer rules= new StringBuffer();
        if(strength!=null){
            rules.append(" [strength ");
            rules.append(getStrength(strength));
            rules.append(" ]");
        }
        String alternate = LDMLUtilities.getAttributeValue(node, LDMLConstants.ALTERNATE);
        if(alternate!=null){
            rules.append(" [alternate ");
            rules.append(alternate);
            rules.append(" ]");
        }
        String backwards = LDMLUtilities.getAttributeValue(node, LDMLConstants.BACKWARDS);
        if(backwards!=null && backwards.equals("on")){
            rules.append(" [backwards 2]");
        }
        String normalization = LDMLUtilities.getAttributeValue(node, LDMLConstants.NORMALIZATION);
        if(normalization!=null){
            rules.append(" [normalization ");
            rules.append(normalization);
            rules.append(" ]");
        }
        String caseLevel = LDMLUtilities.getAttributeValue(node, LDMLConstants.CASE_LEVEL);
        if(caseLevel!=null){
            rules.append(" [caseLevel ");
            rules.append(caseLevel);
            rules.append(" ]");
        }
        
        String caseFirst = LDMLUtilities.getAttributeValue(node, LDMLConstants.CASE_FIRST);
        if(caseFirst!=null){
            rules.append(" [caseFirst ");
            rules.append(caseFirst);
            rules.append(" ]");
        }
        String hiraganaQ = LDMLUtilities.getAttributeValue(node, LDMLConstants.HIRAGANA_Q);
        if(hiraganaQ!=null){
            rules.append(" [hiraganaQ ");
            rules.append(hiraganaQ);
            rules.append(" ]");
        }
        String numeric = LDMLUtilities.getAttributeValue(node, LDMLConstants.NUMERIC);
        if(numeric!=null){
            rules.append(" [numeric ");
            rules.append(numeric);
            rules.append(" ]");
        }
        return rules;
    }
    private static final HashMap collationMap = new HashMap();
    static{
        collationMap.put("first_tertiary_ignorable", "[first tertiary ignorable ]");
        collationMap.put("last_tertiary_ignorable",  "[last tertiary ignorable ]");
        collationMap.put("first_secondary_ignorable","[first secondary ignorable ]");
        collationMap.put("last_secondary_ignorable", "[last secondary ignorable ]");
        collationMap.put("first_primary_ignorable",  "[first primary ignorable ]");
        collationMap.put("last_primary_ignorable",   "[last primary ignorable ]");
        collationMap.put("first_variable",           "[first variable ]");
        collationMap.put("last_variable",            "[last variable ]");
        collationMap.put("first_non_ignorable",      "[first regular]");
        collationMap.put("last_non_ignorable",       "[last regular ]");
        //TODO check for implicit
        //collationMap.put("??",      "[first implicit]");
        //collationMap.put("??",       "[last implicit]");
        collationMap.put("first_trailing",           "[first trailing ]");
        collationMap.put("last_trailing",            "[last trailing ]");
    }
    
    
    private StringBuffer parseRules(Node root, StringBuffer xpath){

        StringBuffer rules = new StringBuffer();
        
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            if(name.equals(LDMLConstants.PC) || name.equals(LDMLConstants.SC) || 
               name.equals(LDMLConstants.TC)|| name.equals(LDMLConstants.QC) || 
               name.equals(LDMLConstants.IC)){
                Node lastVariable = LDMLUtilities.getNode(node, LDMLConstants.LAST_VARIABLE , null, null);
                if(lastVariable!=null){
                    if(DEBUG)rules.append(" ");
                     rules.append(collationMap.get(lastVariable.getNodeName()));
                }else{
                    String data = getData(node,name);
                    rules.append(data);
                }
            }else if(name.equals(LDMLConstants.P) || name.equals(LDMLConstants.S) || 
                    name.equals(LDMLConstants.T)|| name.equals(LDMLConstants.Q) || 
                    name.equals(LDMLConstants.I)){
                Node lastVariable = LDMLUtilities.getNode(node, LDMLConstants.LAST_VARIABLE , null, null);
                if(lastVariable!=null){
                    if(DEBUG) rules.append(" ");
                    rules.append(collationMap.get(lastVariable.getNodeName()));
                }else{

                    String data = getData(node, name);
                    rules.append(data);
                }
            }else if(name.equals(LDMLConstants.X)){
                    rules.append(parseExtension(node));
            }else if(name.equals(LDMLConstants.RESET)){
                rules.append(parseReset(node));
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
        }
        return rules;
    }
    private static final UnicodeSet needsQuoting = new UnicodeSet("[[:whitespace:][:c:][:z:][[:ascii:]-[a-zA-Z0-9]]]");
    private static StringBuffer quoteOperandBuffer = new StringBuffer(); // faster
    private static final String quoteOperand(String s) {
        
        s = Normalizer.normalize(s, Normalizer.NFC);
        quoteOperandBuffer.setLength(0);
        boolean noQuotes = true;
        boolean inQuote = false;
        int cp;
        for (int i = 0; i < s.length(); i += UTF16.getCharCount(cp)) {
            cp = UTF16.charAt(s, i);
            if (!needsQuoting.contains(cp)) {
                if (inQuote) {
                    quoteOperandBuffer.append('\'');
                    inQuote = false;
                }
                quoteOperandBuffer.append(UTF16.valueOf(cp));
            } else {
                noQuotes = false;
                if (cp == '\'') {
                    quoteOperandBuffer.append("''");
                } else {
                    if (!inQuote) {
                        quoteOperandBuffer.append('\'');
                        inQuote = true;
                    }
                    if (cp > 0xFFFF) {
                        quoteOperandBuffer.append("\\U").append(Utility.hex(cp,8));
                    } else if (cp <= 0x20 || cp > 0x7E) {
                        quoteOperandBuffer.append("\\u").append(Utility.hex(cp));
                    } else {
                        quoteOperandBuffer.append(UTF16.valueOf(cp));
                    }
                }
            }
        }
        if (inQuote) {
            quoteOperandBuffer.append('\'');
        }
        if (noQuotes) return s; // faster
        return quoteOperandBuffer.toString();
    }

    
    private String getData(Node root, String strength){
        StringBuffer data = new StringBuffer();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()==Node.ELEMENT_NODE){
                String name = node.getNodeName();
                if(name.equals(LDMLConstants.CP)){
                    String hex = LDMLUtilities.getAttributeValue(node, LDMLConstants.HEX);
                    if(DEBUG)data.append(" ");
                    data.append(getStrengthSymbol(strength));
                    if(DEBUG)data.append(" ");
                    String cp = UTF16.valueOf(Integer.parseInt(hex, 16));
                    data.append(quoteOperand(cp));
                }
            }
            if(node.getNodeType()==Node.TEXT_NODE){
                String val = node.getNodeValue();
                if(val!=null){
                    if(strength.equals(LDMLConstants.PC) || strength.equals(LDMLConstants.SC) || strength.equals(LDMLConstants.TC)|| 
                            strength.equals(LDMLConstants.QC) ||strength.equals(LDMLConstants.IC)){
                        data.append(getExpandedRules(val, strength));
                    }else{
                        if(DEBUG)data.append(" ");
                        data.append(getStrengthSymbol(strength));
                        if(DEBUG)data.append(" ");
                        data.append(quoteOperand(val));
                    }
                }
            }
        }
        return data.toString();
    }
    private String getStrengthSymbol(String name){
        if(name.equals(LDMLConstants.PC) || name.equals(LDMLConstants.P)){
            return "<"; 
        }else if (name.equals(LDMLConstants.SC)||name.equals(LDMLConstants.S)){
            return "<<";
        }else if(name.equals(LDMLConstants.TC)|| name.equals(LDMLConstants.T)){
            return "<<<";
        }else if(name.equals(LDMLConstants.QC) || name.equals(LDMLConstants.Q)){
            return "<<<<";
        }else if(name.equals(LDMLConstants.IC) || name.equals(LDMLConstants.I)){
            return "=";
        }else{
            System.err.println("Encountered strength: "+name);
            System.exit(-1);
        }
        return null;
    }
    
    private String getStrength(String name){
        if(name.equals(LDMLConstants.PRIMARY)){
            return "1"; 
        }else if (name.equals(LDMLConstants.SECONDARY)){
            return "2";
        }else if( name.equals(LDMLConstants.TERTIARY)){
            return "3";
        }else if( name.equals(LDMLConstants.QUARTERNARY)){
            return "4";
        }else if(name.equals(LDMLConstants.IDENTICAL)){
            return "5";
       
        }else{
            System.err.println("Encountered strength: "+name);
            System.exit(-1);
        }
        return null;
    }
        
    private StringBuffer parseReset(Node root){
        /* variableTop   at      & x= [last variable]              <reset>x</reset><i><last_variable/></i>
         *               after   & x  < [last variable]            <reset>x</reset><p><last_variable/></p>
         *               before  & [before 1] x< [last variable]   <reset before="primary">x</reset><p><last_variable/></p>
         */
        /*
         * & [first tertiary ignorable] << \u00e1    <reset><first_tertiary_ignorable/></reset><s>?</s>
         */
        StringBuffer ret = new StringBuffer();
        
        if(DEBUG) ret.append(" ");
        ret.append("&");
        if(DEBUG) ret.append(" ");
        
        String val = LDMLUtilities.getAttributeValue(root, LDMLConstants.BEFORE);
        if(val!=null){
            if(DEBUG) ret.append(" ");
            ret.append("[before ");
            ret.append(getStrength(val));
            ret.append("]");
        }
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            short type = node.getNodeType();
            if(type==Node.ELEMENT_NODE){
 
                String key = node.getNodeName();
                if(DEBUG) ret.append(" ");
                ret.append(collationMap.get(key));
            }
            if(type==Node.TEXT_NODE){
                ret.append(quoteOperand(node.getNodeValue()));
            }
        }
        return ret;
    }
    private StringBuffer getExpandedRules(String data, String name){
        UCharacterIterator iter = UCharacterIterator.getInstance(data);
        StringBuffer ret = new StringBuffer();
        String strengthSymbol = getStrengthSymbol(name);
        int ch;
        while((ch = iter.nextCodePoint() )!= UCharacterIterator.DONE){
            if(DEBUG) ret.append(" ");
            ret.append(strengthSymbol);
            if(DEBUG) ret.append(" ");
            ret.append(quoteOperand(UTF16.valueOf(ch)));
        }
        return ret;
    }
    
    private StringBuffer parseExtension(Node root){
        /*  
         * strength context string extension
         * <strength>  <context> | <string> / <extension>
         * < a | [last variable]      <x><context>a</context><p><last_variable/></p></x>
         * < [last variable]    / a   <x><p><last_variable/></p><extend>a</extend></x>
         * << k / h                   <x><s>k</s> <extend>h</extend></x>
         * << d | a                   <x><context>d</context><s>a</s></x>
         * =  e | a                   <x><context>e</context><i>a</i></x>
         * =  f | a                   <x><context>f</context><i>a</i></x>
         */
         StringBuffer rules = new StringBuffer();
         Node contextNode  =  null;
         Node extendNode   =  null;
         Node strengthNode =  null;
         
         
         String strength = null;
         String string = null;
         String context  = null;
         String extend = null;
         
         for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
             if(node.getNodeType()!=Node.ELEMENT_NODE){
                 continue;
             }
             String name = node.getNodeName();
             if(name.equals(LDMLConstants.CONTEXT)){
                 contextNode = node;
             }else if(name.equals(LDMLConstants.P) || name.equals(LDMLConstants.S) || name.equals(LDMLConstants.T)|| name.equals(LDMLConstants.I)){
                 strengthNode = node;
             }else if(name.equals(LDMLConstants.EXTEND)){
                 extendNode = node;   
             }else{
                 System.err.println("Encountered unknown element: "+name);
                 System.exit(-1);
             }
         }
         if(contextNode != null){
            context = LDMLUtilities.getNodeValue(contextNode);
         }
         if(strengthNode!=null){
             Node lastVariable = LDMLUtilities.getNode(strengthNode, LDMLConstants.LAST_VARIABLE , null, null);
             if(lastVariable!=null){
                 string = (String)collationMap.get(lastVariable.getNodeName());
             }else{
                 strength = getStrengthSymbol(strengthNode.getNodeName());
                 string = LDMLUtilities.getNodeValue(strengthNode);
             }
         }
         if(extendNode!=null){
             extend = LDMLUtilities.getNodeValue(extendNode);
         }
         if(DEBUG) rules.append(" ");
         rules.append(strength);
         if(DEBUG) rules.append(" ");
         if(context!=null){
             rules.append(quoteOperand(context));
             if(DEBUG) rules.append(" ");
             rules.append("|");
             if(DEBUG) rules.append(" ");
         }
         rules.append(string);
         
         if(extend!=null){
             if(DEBUG) rules.append(" ");
             rules.append("/");
             if(DEBUG) rules.append(" ");
             rules.append(quoteOperand(extend));
         }
         return rules;
    }
    
    private ICUResourceWriter.Resource parseSpecialElements(Node root, StringBuffer xpath){
        ICUResourceWriter.Resource current = null, first = null;
        int savedLength = xpath.length();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(ICU_SCRIPTS)){
                 res = parseArray(node, xpath);
                 res.name = LOCALE_SCRIPT;
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    first = res;
                    current = findLast(res);
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(savedLength,xpath.length());
        }
        return first;
   
    }
    private ICUResourceWriter.Resource parseSpecials(Node root){
        
        ICUResourceWriter.Resource current = null, first = null;
        StringBuffer xpath = new StringBuffer();
        xpath.append("//ldml");
        int savedLength = xpath.length();
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if(name.equals(LDMLConstants.IDENTITY)){
                //
                continue;
            }else if(name.equals(LDMLConstants.LDML) || name.equals(LDMLConstants.SPECIAL)){
                //ignore specials for now!
                node=node.getFirstChild();
                continue;
            }else if(name.equals(LDMLConstants.CHARACTERS)){
                res = parseCharacters(node, xpath);
            }else if(name.equals(LDMLConstants.COLLATIONS)){
                //collations are resolved in parseCollation
                continue;
            }else if(name.indexOf("icu:")>-1|| name.indexOf("openOffice:")>-1){
                //TODO: these are specials .. ignore for now ... figure out
                // what to do later
            }else{
                System.err.println("Encountered unknown element: "+name);
                System.exit(-1);
            }
            if(res!=null){
                if(current == null){
                    first = res;
                    current = findLast(res);
                }else{
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(savedLength,xpath.length());
        }
        return first;
    }

    private void writeResource(ICUResourceWriter.Resource set, String sourceFileName){
        try {
            String outputFileName = null;
            outputFileName = destDir+File.separator+set.name+".txt";
            
            FileOutputStream file = new FileOutputStream(outputFileName);
            BufferedOutputStream writer = new BufferedOutputStream(file);
            System.err.println(sourceFileName + ": INFO: Creating ICU ResourceBundle: "+outputFileName);
            //TODO: fix me
            writeHeader(writer,sourceFileName);

            ICUResourceWriter.Resource current = set;
            while(current!=null){
                current.sort();
                current = current.next;
            }
            
            //Now start writing the resource;
            /*ICUResourceWriter.Resource */current = set;
            while(current!=null){
                current.write(writer, 0, false);
                current = current.next;
            }
            writer.flush();
            writer.close();
        } catch (Exception ie) {
            System.err.println(sourceFileName + ": ERROR (writing resource) :" + ie.toString());
            ie.printStackTrace();
            System.exit(1);
            return; // NOTREACHED
        }
    }
    
    private boolean isAlternate(Node node){
        NamedNodeMap attributes = node.getAttributes();
        Node attr = attributes.getNamedItem(LDMLConstants.ALT);
        if(attr!=null){
            return true;
        }
        return false;
    }
    private void writeLine(OutputStream writer, String line) {
        try {
            byte[] bytes = line.getBytes(CHARSET);
            writer.write(bytes, 0, bytes.length);
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }
    private void writeHeader(OutputStream writer, String fileName){
        writeBOM(writer);
        Calendar c = Calendar.getInstance();
        StringBuffer buffer =new StringBuffer();
        buffer.append("// ***************************************************************************" + LINESEP);
        buffer.append("// *" + LINESEP);
        buffer.append("// * Copyright (C) "+c.get(Calendar.YEAR) +" International Business Machines" + LINESEP);
        buffer.append("// * Corporation and others.  All Rights Reserved."+LINESEP);
        buffer.append("// * Tool: com.ibm.icu.dev.tool.cldr.LDML2ICUConverter.java" + LINESEP);
       // buffer.append("// * Date & Time: " + c.get(Calendar.YEAR) + "/" + (c.get(Calendar.MONTH)+1) + "/" + c.get(Calendar.DAY_OF_MONTH) + " " + c.get(Calendar.HOUR_OF_DAY) + COLON + c.get(Calendar.MINUTE)+ LINESEP);
        buffer.append("// * Source File: " + fileName + LINESEP);
        buffer.append("// *" + LINESEP);                    
        buffer.append("// ***************************************************************************" + LINESEP);
        writeLine(writer, buffer.toString());

    }
    
    private  void writeBOM(OutputStream buffer) {
        try {
            byte[] bytes = BOM.getBytes(CHARSET);
            buffer.write(bytes, 0, bytes.length);
        } catch(Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }
}
