use jni::{
    objects::{JClass, JObject, JValueGen, JValueOwned},
    JNIEnv,
};
use thiserror::Error;

pub type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

/// Queries the environment to check if `obj` belongs to the `name` class
/// Errors if it does not
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
        Err(Box::new(InternalJNIError::TypeError {
            expected: expected_class_name,
            got: class_name,
        }))
    }
}

/// Get the name of a class as a String
pub fn get_class_name<'a>(env: &mut JNIEnv<'a>, class: JClass<'a>) -> Result<String> {
    let result = env.call_method(class, "toString", "()Ljava/lang/String;", &[])?;
    let obj = get_object_ref(result)?.into();
    let jstring = env.get_string(&obj)?;
    Ok(jstring.into())
}

/// JNI Errors and internal invariant violations
#[derive(Debug, Error)]
pub enum InternalJNIError {
    #[error("Internal invariant violated, expected member of type `{expected}`, got `{got}`")]
    BadMemberType {
        expected: &'static str,
        got: &'static str,
    },
    #[error("Internal invariant violated. Object passed to jni function was of the wrong class. Expected: `{expected}`, got: `{got}`")]
    TypeError { expected: String, got: String },
    #[error("Null pointer")]
    NullPointer,
    #[error("Index `{idx}` out of bounds for List of length `{len}`")]
    IndexOutOfBounds { len: i32, idx: i32 },
}

/// Given a Java value, extracts the object reference if it exists, otherwise errors
pub fn get_object_ref(v: JValueGen<JObject<'_>>) -> Result<JObject<'_>> {
    match v {
        JValueGen::Object(o) => Ok(o),
        _ => Err(Box::new(InternalJNIError::BadMemberType {
            expected: "object",
            got: value_type(v),
        })),
    }
}

pub fn value_type<T>(v: JValueGen<T>) -> &'static str {
    match v {
        JValueGen::Object(_) => "object",
        JValueGen::Byte(_) => "byte",
        JValueGen::Char(_) => "char",
        JValueGen::Short(_) => "short",
        JValueGen::Int(_) => "int",
        JValueGen::Long(_) => "long",
        JValueGen::Bool(_) => "bool",
        JValueGen::Float(_) => "float",
        JValueGen::Double(_) => "double",
        JValueGen::Void => "void",
    }
}

/// Raises a null-pointer exception (java.lang.NullPointerException)
pub fn raise_npe<'a>(env: &mut JNIEnv<'a>) -> Result<JValueOwned<'a>> {
    env.throw_new("java/lang/NullPointerException", "Null Pointer Exception")?;
    Ok(JValueGen::Object(JObject::null()))
}
