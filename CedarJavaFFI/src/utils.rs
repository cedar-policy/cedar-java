use std::str::Utf8Error;

use itertools::Itertools;
use jni::{
    objects::{JClass, JObject, JValueGen, JValueOwned},
    strings::JavaStr,
    JNIEnv,
};
use thiserror::Error;

pub type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

pub fn get_string(jstring: JavaStr<'_, '_, '_>) -> String {
    jstring.into()
    // match jstring.to_str() {
    //     Ok(s) => Ok(s),
    //     Err(e) => {
    //         let bytes = jstring.to_bytes().to_vec();
    //         Err(Box::new(InternalJNIError::UnicodeError(e, bytes)))
    //     }
    // }
}

pub fn assert_is_class<'a>(env: &mut JNIEnv<'a>, obj: &JObject<'a>, name: &str) -> Result<()> {
    if obj.is_null() {
        raise_npe(env)?;
        return Err(Box::new(InternalJNIError::NullPointer));
    }
    let expected_class = env.find_class(name)?;
    let class = env.get_object_class(obj)?;
    if env.is_same_object(&expected_class, &class)? {
        Ok(())
    } else {
        let class_name = get_class_name(env, class)?;
        let expected_class_name = get_class_name(env, expected_class)?;
        env.throw_new(
            "java/lang/ClassCastException",
            format!("{class_name} cannot be cast to {expected_class_name}"),
        )?;
        Err(Box::new(InternalJNIError::TypeErorr {
            expected: expected_class_name,
            got: class_name,
        }))
    }
}

pub fn get_class_name<'a>(env: &mut JNIEnv<'a>, class: JClass<'a>) -> Result<String> {
    let result = env.call_method(class, "toString", "()Ljava/lang/String;", &[])?;
    let obj = get_object_ref(result)?.into();
    let jstring = env.get_string(&obj)?;
    Ok(get_string(jstring))
}

#[derive(Debug, Error)]
pub enum InternalJNIError {
    #[error("Internal invariant violated, expected member of type `{0}`")]
    BadMemberType(&'static str),
    #[error("Internal invariant violated. Object passed to jni function was of the wrong class. Expected: `{expected}`, got: `{got}`")]
    TypeErorr { expected: String, got: String },
    #[error("Null pointer")]
    NullPointer,
    #[error("Index out of bounds")]
    IndexOutOfBounds,
    #[error("Error decoding string from java: {0} Contained these bytes: [{}]", .1.iter().map(|byte| format!("{:#04x}", byte)).join(", "))]
    UnicodeError(Utf8Error, Vec<u8>),
}

pub fn get_object_ref(v: JValueGen<JObject<'_>>) -> Result<JObject<'_>> {
    match v {
        JValueGen::Object(o) => Ok(o),
        _ => Err(Box::new(InternalJNIError::BadMemberType("String"))),
    }
}

pub fn raise_npe<'a>(env: &mut JNIEnv<'a>) -> Result<JValueOwned<'a>> {
    env.throw_new("java/lang/NullPointerException", "Null Pointer Exception")?;
    Ok(JValueGen::Object(JObject::null()))
}
