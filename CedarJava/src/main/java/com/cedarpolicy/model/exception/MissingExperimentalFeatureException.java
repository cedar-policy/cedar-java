package com.cedarpolicy.model.exception;

import com.cedarpolicy.ExperimentalFeature;

import java.nio.file.Path;

public class MissingExperimentalFeatureException extends InternalException {
    private ExperimentalFeature experimentalFeature;

    public MissingExperimentalFeatureException(ExperimentalFeature experimentalFeature) {
        super("Missing experimental feature. To enable this feature please recompile "
                + Path.of(System.getenv("CEDAR_JAVA_FFI_LIB")).getFileName()
                + " with \"--features=" + experimentalFeature.getCompileFlag() + "\".");
        this.experimentalFeature = experimentalFeature;
    }

    public ExperimentalFeature getExperimentalFeature() {
        return this.experimentalFeature;
    }
}
