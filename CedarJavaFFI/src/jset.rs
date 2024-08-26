use std::marker::PhantomData;

use crate::{objects::Object, utils::Result};
use jni::{
    objects::{JObject, JValueGen},
    JNIEnv,
};

/// Typed wrapper for Java sets
/// (java.util.Set)
#[derive(Debug)]
pub struct Set<'a, T> {
    /// Underlying Java object
    obj: JObject<'a>,
    /// ZST for tracking type info
    marker: PhantomData<T>,
    /// The size of this set
    size: i32,
}

impl<'a, T: Object<'a>> Set<'a, T> {
    /// Construct an empty hash set, which will serve as a set
    pub fn new(env: &mut JNIEnv<'a>) -> Result<Self> {
        let obj = env.new_object("java/util/HashSet", "()V", &[])?;

        Ok(Self {
            obj,
            marker: PhantomData,
            size: 0,
        })
    }

    /// Add an item to the set
    pub fn add(&mut self, env: &mut JNIEnv<'a>, v: T) -> Result<()> {
        let value = JValueGen::Object(v.as_ref());
        env.call_method(&self.obj, "add", "(Ljava/lang/Object;)Z", &[value])?;
        self.size += 1;
        Ok(())
    }
}

impl<'a, T> AsRef<JObject<'a>> for Set<'a, T> {
    fn as_ref(&self) -> &JObject<'a> {
        &self.obj
    }
}
