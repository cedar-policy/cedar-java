package com.cedarpolicy.wasm;

public class CedarEngine implements AutoCloseable {
    private final CedarWasm wasm;

    private CedarEngine(CedarWasm wasm) {
        this.wasm = wasm;
    }

    public static CedarEngine create() {
        return new CedarEngine(CedarWasm.create());
    }

    public String authorize(String requestJson) {
        return wasm.authorize(requestJson);
    }

    public String statefulAuthorize(String requestJson) {
        return wasm.statefulAuthorize(requestJson);
    }

    public String validate(String requestJson) {
        return wasm.call("ValidateOperation", requestJson);
    }

    public String validateEntities(String requestJson) {
        return wasm.call("ValidateEntities", requestJson);
    }

    public String validateWithLevel(String requestJson) {
        return wasm.call("ValidateWithLevelOperation", requestJson);
    }

    public String parsePolicy(String policyText) {
        return wasm.callExport(wasm.exports()::cedarParsePolicy, policyText);
    }

    public String parseTemplate(String templateText) {
        return wasm.callExport(wasm.exports()::cedarParseTemplate, templateText);
    }

    public String policyEffect(String policyText) {
        return wasm.callExport(wasm.exports()::cedarPolicyEffect, policyText);
    }

    public String templateEffect(String templateText) {
        return wasm.callExport(wasm.exports()::cedarTemplateEffect, templateText);
    }

    public String policyToJson(String policyText) {
        return wasm.callExport(wasm.exports()::cedarPolicyToJson, policyText);
    }

    public String policyFromJson(String policyJson) {
        return wasm.callExport(wasm.exports()::cedarPolicyFromJson, policyJson);
    }

    public String getPolicyAnnotations(String policyText) {
        return wasm.callExport(wasm.exports()::cedarGetPolicyAnnotations, policyText);
    }

    public String getTemplateAnnotations(String templateText) {
        return wasm.callExport(wasm.exports()::cedarGetTemplateAnnotations, templateText);
    }

    public String parsePolicies(String policiesText) {
        return wasm.callExport(wasm.exports()::cedarParsePolicies, policiesText);
    }

    public String policySetToJson(String policySetJson) {
        return wasm.callExport(wasm.exports()::cedarPolicySetToJson, policySetJson);
    }

    public String formatPolicies(String policiesText) {
        return wasm.callExport(wasm.exports()::cedarFormatPolicies, policiesText);
    }

    public String parseJsonSchema(String schemaJson) {
        return wasm.callExport(wasm.exports()::cedarParseJsonSchema, schemaJson);
    }

    public String parseCedarSchema(String schemaText) {
        return wasm.callExport(wasm.exports()::cedarParseCedarSchema, schemaText);
    }

    public String schemaToCedar(String schemaJson) {
        return wasm.callExport(wasm.exports()::cedarSchemaToCedar, schemaJson);
    }

    public String schemaToJson(String cedarSchema) {
        return wasm.callExport(wasm.exports()::cedarSchemaToJson, cedarSchema);
    }

    public String getVersion() {
        int widePtr = wasm.exports().cedarVersion();
        return wasm.readWidePtr(widePtr);
    }

    public String preParsePolicySet(String id, String policiesJson) {
        return wasm.callExport(wasm.exports()::cedarPreparsePolicySet, id, policiesJson);
    }

    public String preParseSchema(String id, String schemaJson) {
        return wasm.callExport(wasm.exports()::cedarPreparseSchema, id, schemaJson);
    }

    @Override
    public void close() {}
}
