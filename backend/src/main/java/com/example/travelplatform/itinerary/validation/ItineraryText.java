package com.example.travelplatform.itinerary.validation;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;

/** Shared manual/import text rules; never truncate a user's notes. */
public final class ItineraryText {
    private ItineraryText() { }
    public static String normalize(String raw,int max,boolean required) {
        if(raw==null){if(required)throw invalid();return null;}
        if(raw.codePoints().anyMatch(cp->Character.isISOControl(cp)&&cp!='\t'&&cp!='\r'&&cp!='\n'))throw invalid();
        String value=raw.replace("\r\n","\n").replace('\r','\n');int start=0,end=value.length();
        while(start<end&&space(value.codePointAt(start)))start+=Character.charCount(value.codePointAt(start));
        while(start<end&&space(value.codePointBefore(end)))end-=Character.charCount(value.codePointBefore(end));
        value=value.substring(start,end);if(value.isEmpty()){if(required)throw invalid();return null;}
        if(value.codePointCount(0,value.length())>max)throw invalid();return value;
    }
    private static boolean space(int cp){return Character.isWhitespace(cp)||Character.isSpaceChar(cp);}
    private static ApiException invalid(){return new ApiException(ErrorCode.VALIDATION_FAILED,"行程字段格式或长度不正确");}
}
