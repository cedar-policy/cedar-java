# Changelog

## Unreleased
* Added Schema conversion APIs [#325](https://github.com/cedar-policy/cedar-java/pull/325)
* Added Level Validation [#327](https://github.com/cedar-policy/cedar-java/pull/327)
* Added DateTime extension support [#328](https://github.com/cedar-policy/cedar-java/pull/328)

## 4.3.1
### Added
* Added Zig version validation for publishing artifacts [#306](https://github.com/cedar-policy/cedar-java/pull/306)

## 4.3.0
### Added
* Introduced new model classes for improved type safety and functionality:
  * `com.cedarpolicy.model.Context` - Policy context representation (will replace `Map<String,Value>`) [#286](https://github.com/cedar-policy/cedar-java/pull/286)
  * `com.cedarpolicy.model.entity.Entities` - Entity collection management (will replace `Set<Entity>`) [#293](https://github.com/cedar-policy/cedar-java/pull/293)
* Enhanced `AuthorizationError` with public getters and `.toString()` method [#294](https://github.com/cedar-policy/cedar-java/pull/294)
* Added JSON parsing support for `Entity` [#292](https://github.com/cedar-policy/cedar-java/pull/292)
* Implemented additional constructors to improve instantiation options for `Entity` [#288](https://github.com/cedar-policy/cedar-java/pull/288)
* Added support for policy annotations [#296](https://github.com/cedar-policy/cedar-java/pull/296)

### Planned Improvements
* The following authorization parameters will be updated in a future release:
  * `Map<String,Value>` for context will be replaced by `com.cedarpolicy.model.Context`
  * `Set<Entity>` for entities will be replaced by `com.cedarpolicy.model.entity.Entities`

## 3.0

* Reworked interface of `com.cedarpolicy.value.EntityUID` to support namespaces
* Modified `com.cedarpolicy.model.AuthorizationRequest` to use `com.cedarpolicy.value.EntityUID` instead of Strings
* Removes all use of the deprecated `__expr` syntax in JSON
* Added `com.cedarpolicy.value.EntityTypeName` which represents namespaced types
* Added `com.cedarpolicy.value.EntityIdentifier` which represents Entity Ids

## 2.0.0

Initial release of `CedarJava`.
