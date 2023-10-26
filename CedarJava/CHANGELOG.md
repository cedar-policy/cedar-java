# Changelog

## Unreleased

* Reworked interface of `com.cedarpolicy.value.EntityUID` to support namespaces
* Modified `com.cedarpolicy.model.AuthorizationRequest` to use `com.cedarpolicy.value.EntityUID` instead of Strings
* Removes all use of the deprecated `__expr` syntax in JSON
* Added `com.cedarpolicy.value.EntityTypeName` which represents namespaced types
* Added `com.cedarpolicy.value.EntityIdentifier` which represents Entity Ids

## 2.0.0

Initial release of `CedarJava`.
