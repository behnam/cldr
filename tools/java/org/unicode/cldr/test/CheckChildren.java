package org.unicode.cldr.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.unicode.cldr.test.CheckCLDR.CheckStatus.Subtype;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.Factory;

import com.ibm.icu.impl.Utility;

public class CheckChildren extends FactoryCheckCLDR {
    static final String EMPTY_SET_OVERRIDE = Utility.unescape("\u2205\u2205\u2205");
    CLDRFile[] immediateChildren;
    Map<String, String> tempSet = new HashMap<String, String>();

    public CheckChildren(Factory factory) {
        super(factory);
    }

    public CheckCLDR handleCheck(String path, String fullPath, String value,
        Map<String, String> options, List<CheckStatus> result) {
        if (immediateChildren == null) return this; // skip - test isn't even relevant
        if (isSkipTest()) return this; // disabled
        if (fullPath == null) return this; // skip paths that we don't have
        if (value == null) return this; // skip null values 
        String winningValue = this.getCldrFileToCheck().getWinningValue(fullPath);
        if (!value.equals(winningValue)) {
            return this; // only run this test against winning values.
        }

        // String current = getResolvedCldrFileToCheck().getStringValue(path);
        tempSet.clear();
        for (int i = 0; i < immediateChildren.length; ++i) {
            String otherValue;
            try {
                otherValue = immediateChildren[i].getWinningValue(path);
            } catch (RuntimeException e) {
                throw e;
            }
            if (!otherValue.equals(EMPTY_SET_OVERRIDE)) {
                tempSet.put(immediateChildren[i].getLocaleID(), otherValue);
            } else {
                tempSet.put(immediateChildren[i].getLocaleID(), value);
            }
        }
        if (tempSet.values().contains(value)) return this;

        CheckStatus item = new CheckStatus().setCause(this).setMainType(CheckStatus.errorType)
            .setSubtype(Subtype.valueAlwaysOverridden)
            .setCheckOnSubmit(false)
            .setMessage("Value always overridden in children: {0}", new Object[] { tempSet.keySet().toString() });
        result.add(item);
        tempSet.clear(); // free for gc
        return this;
    }

    public CheckCLDR setCldrFileToCheck(CLDRFile cldrFileToCheck, Map<String, String> options,
        List<CheckStatus> possibleErrors) {
        if (cldrFileToCheck == null) return this;
        if (cldrFileToCheck.getLocaleID().equals("root")) return this; // Root's children can override.

        // Skip if the phase is not final testing
        if (Phase.FINAL_TESTING == getPhase()) {
            setSkipTest(false); // ok
        } else {
            setSkipTest(true);
            return this;
        }

        List<CLDRFile> iChildren = new ArrayList<CLDRFile>();
        super.setCldrFileToCheck(cldrFileToCheck, options, possibleErrors);
        CLDRLocale myLocale = CLDRLocale.getInstance(cldrFileToCheck.getLocaleID());
        Set<CLDRLocale> subLocales = getFactory().subLocalesOf(myLocale);
        if (subLocales == null) return this;
        for (CLDRLocale locale : subLocales) {
            CLDRFile child = getFactory().make(locale.getBaseName(), true);
            if (child == null) {
                CheckStatus item = new CheckStatus().setCause(this).setMainType(CheckStatus.errorType)
                    .setSubtype(Subtype.nullChildFile)
                    .setMessage("Null file from: {0}", new Object[] { locale });
                possibleErrors.add(item);
            } else {
                iChildren.add(child);
            }
        }
        if (iChildren.size() == 0)
            immediateChildren = null;
        else {
            immediateChildren = new CLDRFile[iChildren.size()];
            immediateChildren = (CLDRFile[]) iChildren.toArray(immediateChildren);
        }
        return this;
    }
}
