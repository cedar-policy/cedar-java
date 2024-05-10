use crate::{
    jlist::{jstr_list_to_rust_vec, List},
    utils::{assert_is_class, get_object_ref, Result},
};
use std::{marker::PhantomData, str::FromStr};

use cedar_policy::{EntityId, EntityTypeName, EntityUid};
use jni::{
    objects::{JObject, JString, JValueGen, JValueOwned},
    sys::jvalue,
    JNIEnv,
};

/// General trait for anything that's a wrapper around a java object.
/// This lets us dynamically cast from a Java Object to our typed wrapper
pub trait Object<'a>: Sized + AsRef<JObject<'a>> {
    /// Dynamically cast from an untyped object to our typed wrappers
    fn cast(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self>;
}

impl<'a> Object<'a> for JString<'a> {
    fn cast(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self> {
        assert_is_class(env, &obj, "java/lang/String")?;
        Ok(obj.into())
    }
}

/// Typed wrapper around EntityTypeNames
/// (com.cedarpolicy.value.EntityTypeName)
pub struct JEntityTypeName<'a> {
    obj: JObject<'a>,
    type_name: EntityTypeName,
}

impl<'a> JEntityTypeName<'a> {
    /// Build a new java object
    pub fn new(
        env: &mut JNIEnv<'a>,
        basename: JString<'a>,
        namespace: List<'a, JString<'a>>,
    ) -> Result<Self> {
        let jstr_basename = env.get_string(&basename)?;
        let basename_str = String::from(jstr_basename);
        let mut full_type_name: Vec<String> = jstr_list_to_rust_vec(env, &namespace)?;
        full_type_name.push(basename_str);
        let has_namespace_component_with_colon = full_type_name.iter().any(|s| s.contains("::"));
        if has_namespace_component_with_colon {
            return Err("components of the type name cannot contain colons".into());
        }
        let full_ns_str: String = full_type_name.join("::");
        let type_name: EntityTypeName = full_ns_str.parse()?;
        let obj = env
            .new_object(
                "com/cedarpolicy/value/EntityTypeName",
                "(Ljava/util/List;Ljava/lang/String;)V",
                &[
                    JValueGen::Object(namespace.as_ref()),
                    JValueGen::Object(basename.as_ref()),
                ],
            )
            .unwrap();
        Ok(Self { obj, type_name })
    }

    /// Get the string representation for this EntityTypeName
    pub fn get_string_repr(&self) -> String {
        self.get_rust_repr().to_string()
    }

    /// Decode the java representation into the rust representation
    pub fn get_rust_repr(&self) -> EntityTypeName {
        self.type_name.clone()
    }

    /// Get the namespace field
    pub fn get_namespace(&self, env: &mut JNIEnv<'a>) -> Result<List<'a, JString<'a>>> {
        let v = env.call_method(&self.obj, "getNamespace", "()Ljava/util/List;", &[])?;
        List::cast_unchecked(get_object_ref(v)?, env)
    }

    /// Get the basename field
    pub fn get_basename(&self, env: &mut JNIEnv<'a>) -> Result<JString<'a>> {
        let v = env.call_method(&self.obj, "getBaseName", "()Ljava/lang/String;", &[])?;
        JString::cast(env, get_object_ref(v)?)
    }

    /// Given a rust EntityTypeName, allocate a new Java EntityTypeName object
    pub fn try_from(env: &mut JNIEnv<'a>, etype: &EntityTypeName) -> Result<Self> {
        let basename = env.new_string(etype.basename())?;
        let mut namespace_array = List::new(env)?;
        for part in etype.namespace_components() {
            let part_str = env.new_string(part)?;
            namespace_array.add(env, part_str)?;
        }

        JEntityTypeName::new(env, basename, namespace_array)
    }

    /// Attempt to parse an EntityTypeName from a string, and allocate the result as a Java object
    pub fn parse(env: &mut JNIEnv<'a>, src: &str) -> Result<JOptional<'a, Self>> {
        match EntityTypeName::from_str(src) {
            Ok(etype) => {
                let jetype = Self::try_from(env, &etype)?;
                JOptional::of(env, jetype)
            }
            Err(_) => JOptional::empty(env),
        }
    }
}

impl<'a> Object<'a> for JEntityTypeName<'a> {
    fn cast(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self> {
        assert_is_class(env, &obj, "com/cedarpolicy/value/EntityTypeName")?;
        let namespace = env.call_method(&obj, "getNamespace", "()Ljava/util/List;", &[])?;
        let namespace_components = List::cast_unchecked(get_object_ref(namespace)?, env)?;
        let basename = env.call_method(&obj, "getBaseName", "()Ljava/lang/String;", &[])?;
        let basename = JString::cast(env, get_object_ref(basename)?)?;
        let jstr_basename = env.get_string(&basename)?;
        let basename_str = String::from(jstr_basename);
        let mut full_type_name: Vec<String> = jstr_list_to_rust_vec(env, &namespace_components)?;
        full_type_name.push(basename_str);
        let has_namespace_component_with_colon = full_type_name.iter().any(|s| s.contains("::"));
        if has_namespace_component_with_colon {
            return Err("components of the type name cannot contain colons".into());
        }
        let full_ns_str: String = full_type_name.join("::");
        let type_name: EntityTypeName = full_ns_str.parse()?;
        Ok(Self { obj, type_name })
    }
}

impl<'a> From<JEntityTypeName<'a>> for JObject<'a> {
    fn from(value: JEntityTypeName<'a>) -> Self {
        value.obj
    }
}

impl<'a> AsRef<JObject<'a>> for JEntityTypeName<'a> {
    fn as_ref(&self) -> &JObject<'a> {
        &self.obj
    }
}

/// Typed wrapper representing java Optionals
/// (java.util.optional)
#[derive(Debug)]
pub struct JOptional<'a, T> {
    /// The underlying java object
    value: JValueGen<JObject<'a>>,
    /// ZST that tracks the contained type `T`
    marker: PhantomData<T>,
}

impl<'a, T: AsRef<JObject<'a>>> JOptional<'a, T> {
    /// Construct an empty Optional (equivalent to [Option::None])
    pub fn empty(env: &mut JNIEnv<'a>) -> Result<Self> {
        let value =
            env.call_static_method("java/util/Optional", "empty", "()Ljava/util/Optional;", &[])?;
        Ok(Self {
            value,
            marker: PhantomData,
        })
    }

    /// Construct an optional containing the Java object `t` (equivalent to [`Option::Some`])
    pub fn of(env: &mut JNIEnv<'a>, t: T) -> Result<Self> {
        let value = env.call_static_method(
            "java/util/Optional",
            "of",
            "(Ljava/lang/Object;)Ljava/util/Optional;",
            &[JValueGen::Object(t.as_ref())],
        )?;
        Ok(Self {
            value,
            marker: PhantomData,
        })
    }

    /// Move from a Rust [Option] to a Java optional
    pub fn from_optional(env: &mut JNIEnv<'a>, t: Option<T>) -> Result<Self> {
        match t {
            None => Self::empty(env),
            Some(obj) => Self::of(env, obj),
        }
    }

    /// Get the raw pointer for this value
    pub fn as_jni(&self) -> jvalue {
        self.value.as_jni()
    }
}

impl<'a, T> From<JOptional<'a, T>> for JValueOwned<'a> {
    fn from(value: JOptional<'a, T>) -> Self {
        value.value
    }
}

/// Typed wrapper for EntityIds
/// (com.cedarpolicy.value.EntityIdentifier)
#[derive(Debug)]
pub struct JEntityId<'a> {
    obj: JObject<'a>,
    id: EntityId,
}

impl<'a> JEntityId<'a> {
    /// Construct a new JEntityId object
    pub fn new(env: &mut JNIEnv<'a>, str: JString<'a>) -> Result<Self> {
        let obj = env.new_object(
            "com/cedarpolicy/value/EntityIdentifier",
            "(Ljava/lang/String;)V",
            &[JValueGen::Object(&str)],
        );
        let obj = obj?;
        let jstring = env.get_string(&str)?;
        let src = String::from(jstring);
        let id = match EntityId::from_str(&src) {
            Ok(id) => id,
            Err(empty) => match empty {},
        };
        Ok(Self { obj, id })
    }

    /// Construct a new JEntityId object
    pub fn try_from(env: &mut JNIEnv<'a>, id: &EntityId) -> Result<Self> {
        let jstring = env.new_string(id)?;
        Self::new(env, jstring)
    }

    /// Decode the object into its Rust representation
    pub fn get_rust_repr(&self) -> EntityId {
        self.id.clone()
    }

    /// Decode the object into its string representation
    pub fn get_string_repr(&self) -> String {
        self.id.to_string()
    }
}

impl<'a> Object<'a> for JEntityId<'a> {
    fn cast(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self> {
        assert_is_class(env, &obj, "com/cedarpolicy/value/EntityIdentifier")?;
        let v = env.call_method(&obj, "getId", "()Ljava/lang/String;", &[])?;
        let id_field = get_object_ref(v)?;
        let jstring_obj = JString::cast(env, id_field)?;
        let jstring = env.get_string(&jstring_obj)?;
        let str = String::from(jstring);
        match EntityId::from_str(&str) {
            Ok(id) => Ok(Self { obj, id }),
            Err(empty) => match empty {},
        }
    }
}

impl<'a> AsRef<JObject<'a>> for JEntityId<'a> {
    fn as_ref(&self) -> &JObject<'a> {
        &self.obj
    }
}

/// Typed wrapper for Entity UIDs
/// (com.cedarpolicy.value.EntityUID)
pub struct JEntityUID<'a> {
    obj: JObject<'a>,
    entity_uid: EntityUid,
}

impl<'a> JEntityUID<'a> {
    /// Construct a new EntityUID object
    pub fn new(
        env: &mut JNIEnv<'a>,
        entity_type: JEntityTypeName<'a>,
        id: JEntityId<'a>,
    ) -> Result<Self> {
        let entity_uid: EntityUid =
            EntityUid::from_type_name_and_id(entity_type.get_rust_repr(), id.get_rust_repr());
        let obj = env.new_object(
            "com/cedarpolicy/value/EntityUID",
            "(Lcom/cedarpolicy/value/EntityTypeName;Lcom/cedarpolicy/value/EntityIdentifier;)V",
            &[
                JValueGen::Object(entity_type.as_ref()),
                JValueGen::Object(id.as_ref()),
            ],
        )?;
        Ok(Self { obj, entity_uid })
    }

    /// Attempt to parse an EntityUID from a string, and return the result as a Java optional
    pub fn parse(env: &mut JNIEnv<'a>, src: &str) -> Result<JOptional<'a, Self>> {
        let r: std::result::Result<EntityUid, _> = src.parse();
        match r {
            Ok(eid) => {
                let id = JEntityId::try_from(env, eid.id())?;
                let entity_type = JEntityTypeName::try_from(env, eid.type_name())?;
                let obj = Self::new(env, entity_type, id)?;
                JOptional::of(env, obj)
            }
            Err(_) => JOptional::empty(env),
        }
    }

    /// public method to return the rust representation of this EntityUid
    pub fn get_rust_repr(&self) -> EntityUid {
        self.entity_uid.clone()
    }
}

impl<'a> Object<'a> for JEntityUID<'a> {
    fn cast(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self> {
        assert_is_class(env, &obj, "com/cedarpolicy/value/EntityUID")?;
        let entity_type_name_jref = env.call_method(
            &obj,
            "getType",
            "()Lcom/cedarpolicy/value/EntityTypeName;",
            &[],
        )?;
        let entity_type_name_field = get_object_ref(entity_type_name_jref)?;
        let entity_type_name = JEntityTypeName::cast(env, entity_type_name_field)?;
        let entity_id_jref = env.call_method(
            &obj,
            "getId",
            "()Lcom/cedarpolicy/value/EntityIdentifier;",
            &[],
        )?;
        let entity_id_field = get_object_ref(entity_id_jref)?;
        let entity_id = JEntityId::cast(env, entity_id_field)?;
        let entity_uid = EntityUid::from_type_name_and_id(
            entity_type_name.get_rust_repr(),
            entity_id.get_rust_repr(),
        );
        Ok(Self { obj, entity_uid })
    }
}

impl<'a> AsRef<JObject<'a>> for JEntityUID<'a> {
    fn as_ref(&self) -> &JObject<'a> {
        &self.obj
    }
}
