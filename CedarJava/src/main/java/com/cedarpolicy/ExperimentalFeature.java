package com.cedarpolicy;

public enum ExperimentalFeature {
    /** Partial evaluation feature */
    PARTIAL_EVALUATION("partial-eval");

    private String compileFlag;
    ExperimentalFeature(String compileFlag) {
        this.compileFlag = compileFlag;
    }

    public String getCompileFlag() {
        return this.compileFlag;
    }
}
