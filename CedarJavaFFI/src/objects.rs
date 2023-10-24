use crate::{
    jlist::List,
    utils::{assert_is_class, get_object_ref, get_string, Result},
};
use std::{marker::PhantomData, str::FromStr};

use cedar_policy::{EntityId, EntityTypeName, EntityUid};
use jni::{
    objects::{JObject, JString, JValueGen, JValueOwned},
    sys::jvalue,
    JNIEnv,
};

pub trait Object<'a>: Sized + AsRef<JObject<'a>> {
    fn cast(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self>;
}

impl<'a> Object<'a> for JString<'a> {
    fn cast(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self> {
        assert_is_class(env, &obj, "java/lang/String")?;
        Ok(obj.into())
    }
}

pub struct JEntityTypeName<'a> {
    obj: JObject<'a>,
}

impl<'a> JEntityTypeName<'a> {
    pub fn new(
        env: &mut JNIEnv<'a>,
        basename: JString<'a>,
        namespace: List<'a, JString<'a>>,
    ) -> Result<Self> {
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
        Ok(Self { obj })
    }

    pub fn get_string_repr(&self, env: &mut JNIEnv<'a>) -> Result<String> {
        self.get_rust_repr(env)
            .map(|etype| EntityTypeName::to_string(&etype))
    }

    pub fn get_rust_repr(&self, env: &mut JNIEnv<'a>) -> Result<EntityTypeName> {
        let basename_obj = self.get_basename(env)?;
        let basename_jstring = env.get_string(&basename_obj)?;
        let basename_str = get_string(basename_jstring);
        let namespace = self
            .get_namespace(env)?
            .iter(env)?
            .map(|jstring_obj| {
                env.get_string(&jstring_obj)
                    .map(get_string)
                    .map_err(Into::into)
            })
            .collect::<Result<Vec<_>>>()?;

        let src = if namespace.is_empty() {
            basename_str
        } else {
            let namespace_str = namespace.join("::");
            format!("{namespace_str}::{basename_str}")
        };
        // Parse into a EntityTypeName just for sanity checking.
        let etype = EntityTypeName::from_str(&src)?;
        Ok(etype)
    }

    pub fn get_namespace(&self, env: &mut JNIEnv<'a>) -> Result<List<'a, JString<'a>>> {
        let v = env.call_method(&self.obj, "getNamespace", "()Ljava/util/List;", &[])?;
        Ok(List::cast_unchecked(get_object_ref(v)?))
    }

    pub fn get_basename(&self, env: &mut JNIEnv<'a>) -> Result<JString<'a>> {
        let v = env.call_method(&self.obj, "getBaseName", "()Ljava/lang/String;", &[])?;
        JString::cast(env, get_object_ref(v)?)
    }

    pub fn try_from(env: &mut JNIEnv<'a>, etype: &EntityTypeName) -> Result<Self> {
        let basename = env.new_string(etype.basename())?;
        let mut namespace_array = List::new(env)?;
        for part in etype.namespace_components() {
            let part_str = env.new_string(part)?;
            namespace_array.add(env, part_str)?;
        }

        JEntityTypeName::new(env, basename, namespace_array)
    }

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

        Ok(Self { obj })
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

#[derive(Debug)]
pub struct JOptional<'a, T> {
    value: JValueGen<JObject<'a>>,
    marker: PhantomData<T>,
}

impl<'a, T: AsRef<JObject<'a>>> JOptional<'a, T> {
    pub fn empty(env: &mut JNIEnv<'a>) -> Result<Self> {
        let value =
            env.call_static_method("java/util/Optional", "empty", "()Ljava/util/Optional;", &[])?;
        Ok(Self {
            value,
            marker: PhantomData,
        })
    }

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

    pub fn from_optional(env: &mut JNIEnv<'a>, t: Option<T>) -> Result<Self> {
        match t {
            None => Self::empty(env),
            Some(obj) => Self::of(env, obj),
        }
    }

    pub fn as_jni(&self) -> jvalue {
        self.value.as_jni()
    }
}

impl<'a, T> From<JOptional<'a, T>> for JValueOwned<'a> {
    fn from(value: JOptional<'a, T>) -> Self {
        value.value
    }
}

#[derive(Debug)]
pub struct JEntityId<'a> {
    obj: JObject<'a>,
    id: EntityId,
}

impl<'a> JEntityId<'a> {
    pub fn new(env: &mut JNIEnv<'a>, str: JString<'a>) -> Result<Self> {
        let obj = env.new_object(
            "com/cedarpolicy/value/EntityIdentifier",
            "(Ljava/lang/String;)V",
            &[JValueGen::Object(&str)],
        );
        let obj = obj?;
        let jstring = env.get_string(&str)?;
        let src = get_string(jstring);
        let id = match EntityId::from_str(&src) {
            Ok(id) => id,
            Err(empty) => match empty {},
        };
        Ok(Self { obj, id })
    }

    pub fn try_from(env: &mut JNIEnv<'a>, id: &EntityId) -> Result<Self> {
        let jstring = env.new_string(id)?;
        Self::new(env, jstring)
    }

    pub fn get_rust_repr(&self) -> EntityId {
        self.id.clone()
    }

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
        let str = get_string(jstring);
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

pub struct JEntityUID<'a> {
    obj: JObject<'a>,
}

impl<'a> JEntityUID<'a> {
    pub fn new(
        env: &mut JNIEnv<'a>,
        entity_type: JEntityTypeName<'a>,
        id: JEntityId<'a>,
    ) -> Result<Self> {
        let obj = env.new_object(
            "com/cedarpolicy/value/EntityUID",
            "(Lcom/cedarpolicy/value/EntityTypeName;Lcom/cedarpolicy/value/EntityIdentifier;)V",
            &[
                JValueGen::Object(entity_type.as_ref()),
                JValueGen::Object(id.as_ref()),
            ],
        )?;
        Ok(Self { obj })
    }

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

    pub fn get_repr(&self, env: &mut JNIEnv<'a>) -> Result<String> {
        let etype = self.get_type(env)?.get_string_repr(env)?;
        let id = self.get_id(env)?.get_string_repr();
        let src = format!("{etype}::{id}");
        let euid = EntityUid::from_str(&src)?;
        Ok(euid.to_string())
    }

    pub fn get_type(&self, env: &mut JNIEnv<'a>) -> Result<JEntityTypeName<'a>> {
        let obj = env.call_method(
            &self.obj,
            "getType",
            "()Lcom/cedarpolicy/value/EntityTypeName;",
            &[],
        )?;
        JEntityTypeName::cast(env, get_object_ref(obj)?)
    }

    pub fn get_id(&self, env: &mut JNIEnv<'a>) -> Result<JEntityId<'a>> {
        let obj = env.call_method(
            &self.obj,
            "getId",
            "()Lcom/cedarpolicy/value/EntityIdentifier;",
            &[],
        )?;
        JEntityId::cast(env, get_object_ref(obj)?)
    }
}

impl<'a> Object<'a> for JEntityUID<'a> {
    fn cast(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self> {
        assert_is_class(env, &obj, "com/cedarpolicy/value/EntityUID")?;
        Ok(Self { obj })
    }
}

impl<'a> AsRef<JObject<'a>> for JEntityUID<'a> {
    fn as_ref(&self) -> &JObject<'a> {
        &self.obj
    }
}
