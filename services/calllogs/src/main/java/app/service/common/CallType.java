package app.service.common;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CallType {
    INCOMING, 
    OUTGOING, 
    MISSED, 
    VOICEMAIL, 
    REJECTED, 
    BLOCKED, 
    ANSWERED_EXTERNALLY, 
    UNKNOWN;

    @JsonCreator
    public static CallType from(Object v) {
        if (v == null) return UNKNOWN;
        String s = v.toString().trim().toUpperCase();
        switch (s) {
            case "1": return INCOMING;
            case "2": return OUTGOING;
            case "3": return MISSED;
            case "4": return VOICEMAIL;
            case "5": return REJECTED;
            case "7": return BLOCKED;
            case "8": return ANSWERED_EXTERNALLY;
            default:
                try { return CallType.valueOf(s); } catch (Exception e) { return UNKNOWN; }
        }
    }
}