package org.unicode.cldr.test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.unicode.cldr.test.CheckCLDR.CheckStatus.Subtype;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.XPathParts;

import com.ibm.icu.dev.test.util.PrettyPrinter;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UCharacterDirection;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import com.ibm.icu.util.ULocale;

public class CheckExemplars extends FactoryCheckCLDR {
    
    private static final boolean SUPPRESS_AUX_EMPTY_CHECK = true;

    Collator col;
    Collator spaceCol;
    boolean isRoot;
    PrettyPrinter prettyPrinter;

    static final UnicodeSet HangulSyllables = new UnicodeSet("[[:Hangul_Syllable_Type=LVT:][:Hangul_Syllable_Type=LV:]]").freeze();

    public static final UnicodeSet AlwaysOK = new UnicodeSet("[[[:Nd:][:script=common:][:script=inherited:]-[:Default_Ignorable_Code_Point:]-[:C:] - [_]] [\u066A-\u066C]" +
            "[[؉][་ །༌][ཱ]‎‎{য়}য়]" + // TODO Fix this Hack
    "]").freeze(); //[\\u200c-\\u200f] [:script=common:][:script=inherited:]

    // TODO Fix some of these characters
    private static final UnicodeSet SPECIAL_ALLOW = new UnicodeSet("[\\u200E\\u200F\\u200c\\u200d" +
    		"‎‎‎[\u064B\u064E-\u0651\u0670]‎[:Nd:]‎[\u0951\u0952]‎[\u064B-\u0652\u0654\u0670]‎[\u0A66-\u0A6F][\u0ED0-\u0ED9][\u064B-\u0652]‎[\\u02BB\\u02BC][\u0CE6-\u0CEF]‎‎[\u0966-\u096F]‎]"
            ).freeze(); // add RLM, LRM [\u200C\u200D]‎

    public static final UnicodeSet UAllowedInExemplars = new UnicodeSet("[[:assigned:]-[:Z:]]") // [:alphabetic:][:Mn:][:word_break=Katakana:][:word_break=ALetter:][:word_break=MidLetter:]
    .removeAll(AlwaysOK)
    .addAll(SPECIAL_ALLOW) // add RLM, LRM [\u200C\u200D]‎
    .freeze();

    public static final UnicodeSet AllowedInExemplars = new UnicodeSet(UAllowedInExemplars)
    .removeAll(new UnicodeSet("[[:Uppercase:]-[\u0130]]"))
    .freeze();

    public static final UnicodeSet ALLOWED_IN_PUNCTUATION = new UnicodeSet("[[:P:][:S:]]")
    .freeze();

    public static final UnicodeSet ALLOWED_IN_AUX = new UnicodeSet(AllowedInExemplars)
    .addAll(ALLOWED_IN_PUNCTUATION)
    .removeAll(AlwaysOK)
    .addAll(SPECIAL_ALLOW) // add RLM, LRM [\u200C\u200D]‎
    .freeze();

    public enum ExemplarType {
        main(AllowedInExemplars, "(specific-script - uppercase - invisibles + \u0130)", true), 
        punctuation(ALLOWED_IN_PUNCTUATION, "punctuation", false), 
        auxiliary(ALLOWED_IN_AUX, "(specific-script - uppercase - invisibles + \u0130)", true), 
        index(UAllowedInExemplars, "(specific-script - invisibles)", false), 
        currencySymbol(AllowedInExemplars, "(specific-script - uppercase - invisibles + \u0130)", false);

        public final UnicodeSet allowed;
        public final UnicodeSet toRemove;
        public final String message;
        public final boolean convertUppercase;

        ExemplarType(UnicodeSet allowed, String message, boolean convertUppercase) {
            if (!allowed.isFrozen()) {
                throw new IllegalArgumentException("Internal Error");
            }
            this.allowed = allowed;
            this.message = message;
            this.toRemove = new UnicodeSet(allowed).complement().freeze();
            this.convertUppercase = convertUppercase;
        }
    }
    
    public CheckExemplars(Factory factory) {
        super(factory);
    }

    //Allowed[:script=common:][:script=inherited:][:alphabetic=false:]

    public CheckCLDR setCldrFileToCheck(CLDRFile cldrFileToCheck, Map<String, String> options, List<CheckStatus> possibleErrors) {
        if (cldrFileToCheck == null) return this;
        super.setCldrFileToCheck(cldrFileToCheck, options, possibleErrors);
        String locale = cldrFileToCheck.getLocaleID();
        col = Collator.getInstance(new ULocale(locale));
        spaceCol = Collator.getInstance(new ULocale(locale));
        spaceCol.setStrength(col.PRIMARY);
        isRoot = cldrFileToCheck.getLocaleID().equals("root");
        prettyPrinter = new PrettyPrinter()
        .setOrdering(col != null ? col : Collator.getInstance(ULocale.ROOT))
        .setSpaceComparator(col != null ? col : Collator.getInstance(ULocale.ROOT)
                .setStrength2(Collator.PRIMARY))
                .setCompressRanges(true);
        return this;
    }


    public CheckCLDR handleCheck(String path, String fullPath, String value, Map<String, String> options, List<CheckStatus> result) {
        if (fullPath == null) return this; // skip paths that we don't have
        if (path.indexOf("/exemplarCharacters") < 0) return this;
        XPathParts oparts = new XPathParts(null,null);
        oparts.set(path);
        final String exemplarString = oparts.findAttributeValue("exemplarCharacters", "type");
        ExemplarType type = exemplarString == null ? ExemplarType.main : ExemplarType.valueOf(exemplarString);
        checkExemplar(value, result, type);

        // check relation to auxiliary set
        try {       	
            UnicodeSet mainSet = getResolvedCldrFileToCheck().getExemplarSet("", CLDRFile.WinningChoice.WINNING);
            if (path.indexOf("auxiliary") < 0) {
                // check for auxiliary anyway

                if (!SUPPRESS_AUX_EMPTY_CHECK) {
                    UnicodeSet auxiliarySet = getResolvedCldrFileToCheck().getExemplarSet("auxiliary", CLDRFile.WinningChoice.WINNING);

                    if (auxiliarySet == null) {
                        result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.warningType).setSubtype(Subtype.missingAuxiliaryExemplars)
                                .setMessage("Most languages allow <i>some<i> auxiliary characters, so review this."));   			
                    }
                }
            } else { // auxiliary
                UnicodeSet auxiliarySet = new UnicodeSet(value);
                
                UnicodeSet combined = new UnicodeSet(mainSet).addAll(auxiliarySet);
                checkMixedScripts("main+auxiliary", combined, result);
                
                if (false && auxiliarySet.containsSome(mainSet)) {
                    UnicodeSet overlap = new UnicodeSet(mainSet).retainAll(auxiliarySet).removeAll(HangulSyllables);
                    if (overlap.size() != 0) {
                        String fixedExemplar1 = new PrettyPrinter()
                        .setOrdering(col != null ? col : Collator.getInstance(ULocale.ROOT))
                        .setSpaceComparator(col != null ? col : Collator.getInstance(ULocale.ROOT)
                                .setStrength2(Collator.PRIMARY))
                                .setCompressRanges(true)
                                .format(overlap);
                        result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.warningType).setSubtype(Subtype.auxiliaryExemplarsOverlap)
                                .setMessage("Auxiliary overlaps with main \u200E{0}\u200E", new Object[]{fixedExemplar1}));   			
                    }
                }
            }

            // check for consistency with RTL

            Boolean localeIsRTL = false;
            String opath = getResolvedCldrFileToCheck().getFullXPath("//ldml/layout/orientation");
            oparts.set(opath);
            String lelement = oparts.getElement(-1);
            String charOrientation = oparts.findAttributeValue(lelement,"characters");
            if ( charOrientation.equals("right-to-left")) {
                localeIsRTL = true;
            }

            UnicodeSetIterator mi = new UnicodeSetIterator(mainSet);
            while (mi.next()) {
                if ( mi.codepoint != UnicodeSetIterator.IS_STRING && 
                        ( UCharacter.getDirection(mi.codepoint) == UCharacterDirection.RIGHT_TO_LEFT ||
                                UCharacter.getDirection(mi.codepoint) == UCharacterDirection.RIGHT_TO_LEFT_ARABIC ) &&
                                ! localeIsRTL ) {
                    result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.orientationDisagreesWithExemplars)
                            .setMessage("Main exemplar set contains RTL characters, but orientation of this locale is not RTL."));
                    break;
                }
            }

        } catch (Exception e) {} // if these didn't parse, checkExemplar will be called anyway at some point
        return this;
    }
    
    static final BitSet Japn = new BitSet();
    static final BitSet Kore = new BitSet();
    static {
        Japn.set(UScript.HAN);
        Japn.set(UScript.HIRAGANA);
        Japn.set(UScript.KATAKANA);
        Kore.set(UScript.HAN);
        Kore.set(UScript.HANGUL);
    }
    
    private void checkMixedScripts(String title, UnicodeSet set, List<CheckStatus> result) {
        BitSet s = new BitSet();
        for (String item : set) {
            int script = UScript.getScript(item.codePointAt(0));
            if (script != UScript.COMMON && script != UScript.INHERITED) {
                s.set(script);
            }
        }
        final int cardinality = s.cardinality();
        if (cardinality < 2) {
            return;
        }
        if (cardinality == 2 && title.equals("currencySymbol") && s.get(UScript.LATIN)) {
            return; // allow 2 scripts in exemplars for currencies.
        }
        // allowable combinations
        if (s.equals(Japn) || s.equals(Kore)) {
            return;
        }
        StringBuilder scripts = new StringBuilder();
        for (int i = s.nextSetBit(0); i >= 0; i = s.nextSetBit(i+1)) {
            if (scripts.length() != 0) {
                scripts.append(", ");
            }
            scripts.append(UScript.getName(i));
            UnicodeSet inSet = new UnicodeSet().applyIntPropertyValue(UProperty.SCRIPT, i).retainAll(set);
            int count = 0;
            scripts.append(" (");
            for (String cp : inSet) {
                if (count != 0) {
                    scripts.append(",");
                }
                scripts.append(cp);
                count++;
                if (count > 3) {
                    scripts.append('…');
                    break;
                }
            }
            scripts.append(")");
        }
        result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.illegalExemplarSet)
                .setMessage("{0} exemplars contain multiple scripts: {1}", new Object[]{title, scripts}));
        return;
    }

    private void checkExemplar(String v, List<CheckStatus> result, ExemplarType exemplarType) {
        if (v == null) return;
        final UnicodeSet exemplar1;
        try {
            exemplar1 = new UnicodeSet(v).freeze();
        } catch (Exception e) {
            result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.illegalExemplarSet)
                    .setMessage("This field must be a set of the form [a b c-d ...]: ", new Object[]{e.getMessage()}));
            return;
        }

        // check for mixed scripts
        
        checkMixedScripts(exemplarType.toString(), exemplar1, result);
        
        // check that the formatting is correct

        String fixedExemplar1 = prettyPrinter.format(exemplar1);
        UnicodeSet doubleCheck = new UnicodeSet(fixedExemplar1);
        if (!doubleCheck.equals(exemplar1)) {
            result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.internalUnicodeSetFormattingError)
                    .setMessage("Internal Error: formatting not working for {0}", new Object[]{exemplar1}));
        }
        //    	else if (!v.equals(fixedExemplar1)) {
        //	    	result.add(new CheckStatus().setCause(this).setType(CheckStatus.warningType)
        //	    	.setMessage("Better formatting would be \u200E{0}\u200E", new Object[]{fixedExemplar1}));
        //    	}

        // now check that only allowed characters are in the set

        if (!exemplarType.allowed.containsAll(exemplar1)) {
            UnicodeSet remainder0 = new UnicodeSet(exemplar1).removeAll(exemplarType.allowed);

            // we do allow for punctuation & combining marks in strings
            UnicodeSet remainder = new UnicodeSet();
            for (String s : remainder0) {
                if (Character.codePointCount(s, 0, s.length()) == 1) {
                    remainder.add(s);
                } else {
                    // just check normalization
                }
            }

            // after a first check, we check again in case we flattened

            if (remainder.size() != 0) {
                fixedExemplar1 = prettyPrinter.format(exemplar1);
                result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.warningType).setSubtype(Subtype.illegalCharactersInExemplars)
                        .setMessage("Should be limited to " + exemplarType.message + "; thus not contain: \u200E{0}\u200E",
                                new Object[]{remainder}));
            }
        }

        // now check for empty

        if (!isRoot && exemplar1.size() == 0) {
            switch(exemplarType) {
            case currencySymbol: // ok if empty
                break; 
            case auxiliary:
                result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.warningType).setSubtype(Subtype.missingAuxiliaryExemplars)
                        .setMessage("Most languages allow <i>some<i> auxiliary characters, so review this."));   
                break;
            case index:
            case punctuation:
            case main:
                result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.missingMainExemplars)
                        .setMessage("Exemplar set (" + exemplarType + ") must not be empty -- that would imply that this language uses no " +
                                (exemplarType == ExemplarType.punctuation ? "punctuation" : "letters") + "!"));   
                break;
            }
        }
    }
}
