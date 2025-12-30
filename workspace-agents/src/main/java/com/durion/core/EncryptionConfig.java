package com.durion.core;

public class EncryptionConfig {
    private final String secretType;
    private final String algorithm;

    public EncryptionConfig(String secretType, String algorithm) {
        this.secretType = secretType;
        this.algorithm = algorithm;
    }

    public String getSecretType() { return secretType; }
    public String getAlgorithm() { return algorithm; }
}
