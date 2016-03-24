package org.unicode.cldr.tool;

import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.Factory;

public class CompareOyster {

    public static void main(String[] args) {
        Factory factory = CLDRConfig.getInstance().getCldrFactory();
        CLDRFile file = null;
        String lastLocale = null;
        for (String[] item : items) {
            String itemLocale = item[0];
            String itemRegion = item[1];
            if (!itemLocale.equals(lastLocale)) {
                file = factory.make(itemLocale, true, DraftStatus.contributed);
                lastLocale = itemLocale;
            }
            System.out.println(itemLocale + "\t" + itemRegion + "\t"
                + file.getName(CLDRFile.TERRITORY_NAME, itemRegion));
        }
    }

    static final String[][] items = {
        { "af", "AZ" },
        { "af", "BF" },
        { "af", "BV" },
        { "af", "CC" },
        { "af", "IO" },
        { "af", "KN" },
        { "af", "LC" },
        { "af", "MD" },
        { "af", "MP" },
        { "af", "MR" },
        { "af", "NR" },
        { "af", "PG" },
        { "af", "PM" },
        { "af", "SB" },
        { "af", "ST" },
        { "af", "UA" },
        { "af", "VC" },
        { "ar", "AI" },
        { "ar", "AW" },
        { "ar", "BS" },
        { "ar", "CC" },
        { "bg", "AX" },
        { "bg", "BL" },
        { "bg", "BW" },
        { "bg", "CI" },
        { "bg", "FM" },
        { "bg", "IO" },
        { "bg", "IR" },
        { "bg", "JE" },
        { "bg", "KM" },
        { "bg", "KR" },
        { "bg", "LA" },
        { "bg", "LY" },
        { "bg", "MD" },
        { "bg", "NF" },
        { "bg", "NL" },
        { "bg", "PM" },
        { "bg", "PN" },
        { "bg", "RE" },
        { "bg", "RU" },
        { "bg", "ST" },
        { "bg", "SY" },
        { "bg", "SZ" },
        { "bg", "TC" },
        { "bg", "TF" },
        { "bg", "VC" },
        { "bg", "VG" },
        { "bg", "VI" },
        { "cs", "CX" },
        { "cs", "TF" },
        { "da", "AX" },
        { "da", "CI" },
        { "da", "CW" },
        { "da", "DO" },
        { "da", "NC" },
        { "da", "NF" },
        { "da", "VC" },
        { "da", "VI" },
        { "de", "AX" },
        { "de", "BQ" },
        { "de", "KP" },
        { "de", "KR" },
        { "de", "RU" },
        { "el", "GG" },
        { "el", "JE" },
        { "el", "KM" },
        { "el", "KN" },
        { "el", "MK" },
        { "el", "NR" },
        { "el", "PM" },
        { "el", "SZ" },
        { "el", "TF" },
        { "el", "TL" },
        { "el", "US" },
        { "el", "WF" },
        { "es", "CX" },
        { "es", "NU" },
        { "et", "BL" },
        { "et", "IM" },
        { "et", "NF" },
        { "et", "PM" },
        { "eu", "AE" },
        { "eu", "BL" },
        { "eu", "BO" },
        { "eu", "BQ" },
        { "eu", "CC" },
        { "eu", "FO" },
        { "eu", "GG" },
        { "eu", "KE" },
        { "eu", "MY" },
        { "eu", "QA" },
        { "eu", "TH" },
        { "eu", "TJ" },
        { "fa", "AE" },
        { "fa", "AI" },
        { "fa", "AS" },
        { "fa", "AX" },
        { "fa", "BQ" },
        { "fa", "BV" },
        { "fa", "BY" },
        { "fa", "CC" },
        { "fa", "CF" },
        { "fa", "CV" },
        { "fa", "CW" },
        { "fa", "CX" },
        { "fa", "DM" },
        { "fa", "GD" },
        { "fa", "GI" },
        { "fa", "GQ" },
        { "fa", "GS" },
        { "fa", "GU" },
        { "fa", "GW" },
        { "fa", "HM" },
        { "fa", "IM" },
        { "fa", "IO" },
        { "fa", "KI" },
        { "fa", "KM" },
        { "fa", "NF" },
        { "fa", "NU" },
        { "fa", "NZ" },
        { "fa", "PG" },
        { "fa", "RE" },
        { "fa", "SM" },
        { "fa", "ST" },
        { "fa", "TC" },
        { "fa", "TL" },
        { "fa", "US" },
        { "fa", "WF" },
        { "fa", "WS" },
        { "fa", "ZA" },
        { "fi", "GB" },
        { "fi", "HM" },
        { "fi", "TF" },
        { "fr", "BL" },
        { "fr", "CC" },
        { "fr", "MF" },
        { "fr", "PN" },
        { "fr", "ST" },
        { "fr", "VC" },
        { "gl", "AX" },
        { "gl", "CC" },
        { "gl", "CF" },
        { "gl", "DJ" },
        { "gl", "EH" },
        { "gl", "FJ" },
        { "gl", "GW" },
        { "gl", "JE" },
        { "gl", "KE" },
        { "gl", "KN" },
        { "gl", "KZ" },
        { "gl", "LS" },
        { "gl", "MD" },
        { "gl", "MK" },
        { "gl", "ML" },
        { "gl", "MS" },
        { "gl", "NZ" },
        { "gl", "PW" },
        { "gl", "SV" },
        { "gl", "TK" },
        { "gl", "TM" },
        { "he", "AI" },
        { "he", "AZ" },
        { "he", "GN" },
        { "he", "GS" },
        { "he", "GW" },
        { "he", "HM" },
        { "he", "IO" },
        { "he", "KM" },
        { "he", "KP" },
        { "he", "KR" },
        { "he", "MF" },
        { "he", "MV" },
        { "he", "PG" },
        { "he", "PR" },
        { "he", "VI" },
        { "hi", "AG" },
        { "hi", "AI" },
        { "hi", "AX" },
        { "hi", "BA" },
        { "hi", "BH" },
        { "hi", "BS" },
        { "hi", "BZ" },
        { "hi", "FJ" },
        { "hi", "GF" },
        { "hi", "GS" },
        { "hi", "GT" },
        { "hi", "HN" },
        { "hi", "HT" },
        { "hi", "ID" },
        { "hi", "IL" },
        { "hi", "IO" },
        { "hi", "KG" },
        { "hi", "KZ" },
        { "hi", "LU" },
        { "hi", "MD" },
        { "hi", "MH" },
        { "hi", "MS" },
        { "hi", "PW" },
        { "hi", "PY" },
        { "hi", "RW" },
        { "hi", "SL" },
        { "hi", "SR" },
        { "hi", "ST" },
        { "hi", "TL" },
        { "hi", "TN" },
        { "hi", "TT" },
        { "hi", "TV" },
        { "hi", "UY" },
        { "hi", "UZ" },
        { "hr", "AI" },
        { "hr", "AQ" },
        { "hr", "AX" },
        { "hr", "CI" },
        { "hr", "CK" },
        { "hr", "CV" },
        { "hr", "GB" },
        { "hr", "GP" },
        { "hr", "GS" },
        { "hr", "HM" },
        { "hr", "IO" },
        { "hr", "KZ" },
        { "hr", "MP" },
        { "hr", "MS" },
        { "hr", "SB" },
        { "hr", "SV" },
        { "hr", "TC" },
        { "hr", "TF" },
        { "hr", "VA" },
        { "hr", "YT" },
        { "hu", "AE" },
        { "hu", "AX" },
        { "hu", "BL" },
        { "hu", "CX" },
        { "hu", "DM" },
        { "hu", "FJ" },
        { "hu", "GS" },
        { "hu", "GW" },
        { "hu", "HM" },
        { "hu", "LC" },
        { "hu", "MF" },
        { "hu", "MP" },
        { "hu", "RE" },
        { "hu", "RU" },
        { "hu", "SH" },
        { "hu", "ST" },
        { "hu", "TJ" },
        { "hu", "WF" },
        { "id", "AD" },
        { "id", "CX" },
        { "id", "GB" },
        { "id", "GS" },
        { "id", "IO" },
        { "id", "KY" },
        { "id", "NF" },
        { "id", "VI" },
        { "id", "WF" },
        { "is", "CK" },
        { "is", "CL" },
        { "is", "FM" },
        { "is", "NL" },
        { "is", "PN" },
        { "is", "SC" },
        { "it", "HM" },
        { "it", "IO" },
        { "it", "PN" },
        { "it", "RU" },
        { "it", "VC" },
        { "ja", "CC" },
        { "ja", "LU" },
        { "ja", "MV" },
        { "ja", "NU" },
        { "ja", "RE" },
        { "ja", "YT" },
        { "ka", "AI" },
        { "ka", "AS" },
        { "ka", "BL" },
        { "ka", "BT" },
        { "ka", "BV" },
        { "ka", "BY" },
        { "ka", "CC" },
        { "ka", "DO" },
        { "ka", "GF" },
        { "ka", "GP" },
        { "ka", "IO" },
        { "ka", "LI" },
        { "ka", "MD" },
        { "ka", "MV" },
        { "ka", "PF" },
        { "ka", "TC" },
        { "ka", "TL" },
        { "ka", "VG" },
        { "ka", "VI" },
        { "ka", "ZA" },
        { "ko", "AI" },
        { "ko", "BL" },
        { "ko", "BV" },
        { "ko", "CF" },
        { "ko", "CV" },
        { "ko", "CY" },
        { "ko", "FO" },
        { "ko", "GE" },
        { "ko", "GW" },
        { "ko", "HM" },
        { "ko", "IO" },
        { "ko", "KM" },
        { "ko", "KN" },
        { "ko", "KP" },
        { "ko", "MQ" },
        { "ko", "NC" },
        { "ko", "PM" },
        { "ko", "SC" },
        { "ko", "SZ" },
        { "ko", "TC" },
        { "ko", "VA" },
        { "ko", "VG" },
        { "ko", "VI" },
        { "ko", "WF" },
        { "ko", "YT" },
        { "lt", "AX" },
        { "lt", "BL" },
        { "lt", "BV" },
        { "lt", "GP" },
        { "lt", "HM" },
        { "lt", "KG" },
        { "lt", "LC" },
        { "lt", "MV" },
        { "lt", "NF" },
        { "lt", "PF" },
        { "lt", "SH" },
        { "lt", "SJ" },
        { "lv", "HM" },
        { "lv", "SC" },
        { "lv", "SJ" },
        { "lv", "ZA" },
        { "nb", "BA" },
        { "nb", "BL" },
        { "nb", "BN" },
        { "nb", "IM" },
        { "nb", "IO" },
        { "nb", "KN" },
        { "nb", "NF" },
        { "nb", "SA" },
        { "nb", "TD" },
        { "nb", "YE" },
        { "nl", "BL" },
        { "nl", "IO" },
        { "nl", "MF" },
        { "nl", "MP" },
        { "nl", "PM" },
        { "nl", "TF" },
        { "nb", "BL" },
        { "nb", "GS" },
        { "nb", "HM" },
        { "pl", "DO" },
        { "pl", "FM" },
        { "pl", "GG" },
        { "pl", "IO" },
        { "pl", "JE" },
        { "pl", "TF" },
        { "pt", "CX" },
        { "pt", "GL" },
        { "pt", "GS" },
        { "pt", "GW" },
        { "pt", "KY" },
        { "pt", "SX" },
        { "pt", "UM" },
        { "pt", "VI" },
        { "pt_PT", "MU" },
        { "pt_PT", "PG" },
        { "pt_PT", "PM" },
        { "pt_PT", "TF" },
        { "pt_PT", "TL" },
        { "ro", "AX" },
        { "ro", "BL" },
        { "ro", "BY" },
        { "ro", "PM" },
        { "ro", "PN" },
        { "ro", "PR" },
        { "ro", "ST" },
        { "ro", "VC" },
        { "ro", "VI" },
        { "ru", "GS" },
        { "ru", "MV" },
        { "ru", "NU" },
        { "ru", "PN" },
        { "ru", "TC" },
        { "sk", "AG" },
        { "sk", "AX" },
        { "sk", "GY" },
        { "sk", "IO" },
        { "sk", "KN" },
        { "sk", "MF" },
        { "sk", "SC" },
        { "sk", "ST" },
        { "sk", "SV" },
        { "sk", "VI" },
        { "sl", "AX" },
        { "sl", "CK" },
        { "sl", "GB" },
        { "sl", "PN" },
        { "sl", "ZA" },
        { "sr", "AG" },
        { "sr", "AM" },
        { "sr", "AX" },
        { "sr", "BS" },
        { "sr", "BV" },
        { "sr", "CC" },
        { "sr", "CF" },
        { "sr", "CV" },
        { "sr", "CX" },
        { "sr", "GG" },
        { "sr", "GP" },
        { "sr", "GS" },
        { "sr", "GW" },
        { "sr", "HM" },
        { "sr", "IO" },
        { "sr", "KG" },
        { "sr", "KM" },
        { "sr", "LC" },
        { "sr", "MF" },
        { "sr", "PN" },
        { "sr", "PR" },
        { "sr", "PT" },
        { "sr", "SB" },
        { "sr", "TF" },
        { "sr", "VI" },
        { "sr", "WF" },
        { "sv", "SX" },
        { "sv", "TK" },
        { "te", "AD" },
        { "te", "AE" },
        { "te", "AF" },
        { "te", "AR" },
        { "te", "BA" },
        { "te", "BD" },
        { "te", "BF" },
        { "te", "BH" },
        { "te", "BM" },
        { "te", "BN" },
        { "te", "BR" },
        { "te", "BW" },
        { "te", "BZ" },
        { "te", "CF" },
        { "te", "CH" },
        { "te", "CI" },
        { "te", "CL" },
        { "te", "CM" },
        { "te", "CR" },
        { "te", "CV" },
        { "te", "CZ" },
        { "te", "DE" },
        { "te", "DO" },
        { "te", "EC" },
        { "te", "EG" },
        { "te", "ES" },
        { "te", "FJ" },
        { "te", "GA" },
        { "te", "GB" },
        { "te", "GH" },
        { "te", "GL" },
        { "te", "HN" },
        { "te", "HR" },
        { "te", "HU" },
        { "te", "IE" },
        { "te", "IL" },
        { "te", "IN" },
        { "te", "IT" },
        { "te", "JE" },
        { "te", "JO" },
        { "te", "KG" },
        { "te", "KZ" },
        { "te", "LT" },
        { "te", "LV" },
        { "te", "MA" },
        { "te", "MD" },
        { "te", "MG" },
        { "te", "MK" },
        { "te", "MR" },
        { "te", "NL" },
        { "te", "NO" },
        { "te", "NZ" },
        { "te", "OM" },
        { "te", "PH" },
        { "te", "PN" },
        { "te", "PR" },
        { "te", "RO" },
        { "te", "RU" },
        { "te", "RW" },
        { "te", "SB" },
        { "te", "SD" },
        { "te", "SG" },
        { "te", "SO" },
        { "te", "TH" },
        { "te", "TJ" },
        { "te", "TL" },
        { "te", "TM" },
        { "te", "TT" },
        { "te", "UA" },
        { "te", "UG" },
        { "te", "US" },
        { "te", "VA" },
        { "te", "VN" },
        { "te", "VU" },
        { "te", "YE" },
        { "te", "ZA" },
        { "th", "CI" },
        { "tr", "BL" },
        { "tr", "CC" },
        { "tr", "GP" },
        { "uk", "BA" },
        { "uk", "BL" },
        { "uk", "BM" },
        { "uk", "BS" },
        { "uk", "CZ" },
        { "uk", "DJ" },
        { "uk", "DM" },
        { "uk", "GW" },
        { "uk", "GY" },
        { "uk", "HM" },
        { "uk", "IO" },
        { "uk", "MS" },
        { "uk", "NU" },
        { "uk", "SC" },
        { "uk", "SK" },
        { "uk", "TC" },
        { "uk", "TL" },
        { "uk", "TM" },
        { "uk", "VG" },
        { "uk", "VI" },
        { "uk", "YT" },
        { "uk", "ZA" },
        { "vi", "AE" },
        { "vi", "AS" },
        { "vi", "AX" },
        { "vi", "BL" },
        { "vi", "CM" },
        { "vi", "CU" },
        { "vi", "GF" },
        { "vi", "LB" },
        { "vi", "SA" },
        { "vi", "TC" },
        { "vi", "TL" },
        { "zh", "BQ" },
        { "zh", "BV" },
        { "zh", "CC" },
        { "zh", "HM" },
        { "zh_Hant_HK", "AE" },
        { "zh_Hant_HK", "AW" },
        { "zh_Hant_HK", "AZ" },
        { "zh_Hant_HK", "BA" },
        { "zh_Hant_HK", "CN" },
        { "zh_Hant_HK", "CR" },
        { "zh_Hant_HK", "CV" },
        { "zh_Hant_HK", "ET" },
        { "zh_Hant_HK", "GA" },
        { "zh_Hant_HK", "GE" },
        { "zh_Hant_HK", "GM" },
        { "zh_Hant_HK", "GT" },
        { "zh_Hant_HK", "HN" },
        { "zh_Hant_HK", "IT" },
        { "zh_Hant_HK", "KE" },
        { "zh_Hant_HK", "LC" },
        { "zh_Hant_HK", "LR" },
        { "zh_Hant_HK", "ME" },
        { "zh_Hant_HK", "ML" },
        { "zh_Hant_HK", "MU" },
        { "zh_Hant_HK", "MV" },
        { "zh_Hant_HK", "MZ" },
        { "zh_Hant_HK", "NC" },
        { "zh_Hant_HK", "NG" },
        { "zh_Hant_HK", "OM" },
        { "zh_Hant_HK", "QA" },
        { "zh_Hant_HK", "SA" },
        { "zh_Hant_HK", "SO" },
        { "zh_Hant_HK", "ST" },
        { "zh_Hant_HK", "SZ" },
        { "zh_Hant_HK", "TC" },
        { "zh_Hant_HK", "TD" },
        { "zh_Hant_HK", "TV" },
        { "zh_Hant_HK", "TZ" },
        { "zh_Hant_HK", "VU" },
        { "zh_Hant", "AI" },
        { "zh_Hant", "AS" },
        { "zh_Hant", "AW" },
        { "zh_Hant", "BA" },
        { "zh_Hant", "BF" },
        { "zh_Hant", "CC" },
        { "zh_Hant", "DJ" },
        { "zh_Hant", "GE" },
        { "zh_Hant", "GP" },
        { "zh_Hant", "GW" },
        { "zh_Hant", "GY" },
        { "zh_Hant", "HM" },
        { "zh_Hant", "IO" },
        { "zh_Hant", "KM" },
        { "zh_Hant", "LI" },
        { "zh_Hant", "NC" },
        { "zh_Hant", "PN" },
        { "zh_Hant", "ST" },
        { "zh_Hant", "TC" },
        { "zh_Hant", "TG" },
        { "zh_Hant", "UM" },
        { "zh_Hant", "ZW" },
    };
}
