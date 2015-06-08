package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The policies that govern password creation. New studies will be created with the default password 
 * policy, which requires 8 characters, one numeric, one symbol and one upper- and lower-case 
 * character. However, this policy can be adjusted for legacy studies which were created with much 
 * more lax constraints.
 */
public final class PasswordPolicy {
    
    public static final int FIXED_MAX_LENGTH = 100;
    public static final int VALUE_OFF = 0;
    public static final int VALUE_ON = 1;
    
    /**
     * The password policy that is initially created for a study.
     */
    public static final PasswordPolicy DEFAULT_PASSWORD_POLICY = new PasswordPolicy(8, true, true, true);
    
    private final int minLength;
    private final boolean requireNumeric;
    private final boolean requireSymbol;
    private final boolean requireUpperCase;
    
    @JsonCreator
    public PasswordPolicy(@JsonProperty("minLenght") int minLength,
                    @JsonProperty("requireNumeric") boolean requireNumeric,
                    @JsonProperty("requireSymbol") boolean requireSymbol,
                    @JsonProperty("requireUpperCase") boolean requireUpperCase) {
        this.minLength = minLength;
        this.requireNumeric = requireNumeric;
        this.requireSymbol = requireSymbol;
        this.requireUpperCase = requireUpperCase;
    }
    
    public int getMinLength() {
        return minLength;
    }
    public boolean isNumericRequired() {
        return requireNumeric;
    }
    public boolean isSymbolRequired() {
        return requireSymbol;
    }
    public boolean isUpperCaseRequired() {
        return requireUpperCase;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(minLength);
        result = prime * result + Objects.hashCode(requireNumeric);
        result = prime * result + Objects.hashCode(requireSymbol);
        result = prime * result + Objects.hashCode(requireUpperCase);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        PasswordPolicy other = (PasswordPolicy) obj;
        return (Objects.equals(minLength, other.minLength) && 
                Objects.equals(requireNumeric, other.requireNumeric) &&
                Objects.equals(requireSymbol, other.requireSymbol) &&
                Objects.equals(requireUpperCase, other.requireUpperCase));
    }

    @Override
    public String toString() {
        return String.format("PasswordPolicy [minLength=%s, requireNumeric=%s, requireSymbol=%s, requireUpperCase=%s]",
            minLength, requireNumeric, requireUpperCase, requireSymbol);
    }
     
    
}
