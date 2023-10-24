use std::marker::PhantomData;

use crate::{
    objects::Object,
    utils::{get_object_ref, InternalJNIError, Result},
};
use jni::{
    objects::{JObject, JValueGen},
    JNIEnv,
};

/// Typed wrapper for Java lists
/// (java.util.List)
#[derive(Debug)]
pub struct List<'a, T> {
    /// Underlying Java object
    obj: JObject<'a>,
    /// ZST for tracking type info
    marker: PhantomData<T>,
}

impl<'a, T: Object<'a>> List<'a, T> {
    /// Construct an empty array list, which will serve as a list
    pub fn new(env: &mut JNIEnv<'a>) -> Result<Self> {
        let obj = env.new_object("java/util/ArrayList", "()V", &[])?;
        Ok(Self {
            obj,
            marker: PhantomData,
        })
    }

    /// Add an item to the back of the list
    pub fn add(&mut self, env: &mut JNIEnv<'a>, v: T) -> Result<()> {
        let value = JValueGen::Object(v.as_ref());
        env.call_method(&self.obj, "add", "(Ljava/lang/Object;)Z", &[value])?;
        Ok(())
    }

    /// Cast from an untyped java object to this wrapper
    /// We can't check this as I don't see a way to list a class's interfaces
    pub fn cast_unchecked(obj: JObject<'a>) -> Self {
        Self {
            obj,
            marker: PhantomData,
        }
    }

    /// Get the object at position `i`, throws an exception if out-of-bounds
    pub fn get(&self, env: &mut JNIEnv<'a>, i: i32) -> Result<T> {
        let v = env.call_method(
            &self.obj,
            "get",
            "(I)Ljava/lang/Object;",
            &[JValueGen::Int(i)],
        )?;
        // `.get()` throws on index out of bounds
        if env.exception_check()? {
            Err(Box::new(InternalJNIError::IndexOutOfBounds))
        } else {
            T::cast(env, get_object_ref(v)?)
        }
    }

    /// Iterate over the elements in the list
    pub fn iter(&self, env: &mut JNIEnv<'a>) -> Result<impl Iterator<Item = T>> {
        let max = match env.call_method(&self.obj, "size", "()I", &[])? {
            JValueGen::Int(x) => Ok(x),
            _ => Err(Box::new(InternalJNIError::BadMemberType("int"))),
        }?;
        let mut v = vec![];
        for i in 0..max {
            v.push(self.get(env, i)?);
        }
        Ok(v.into_iter())
    }
}

impl<'a, T> AsRef<JObject<'a>> for List<'a, T> {
    fn as_ref(&self) -> &JObject<'a> {
        &self.obj
    }
}
