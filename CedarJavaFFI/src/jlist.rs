/*
 * Copyright Cedar Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::marker::PhantomData;

use crate::{
    objects::Object,
    utils::{get_object_ref, value_type, InternalJNIError, Result},
};
use jni::{
    objects::{JObject, JString, JValueGen},
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
    /// The length of this list
    size: i32,
}

impl<'a, T: Object<'a>> List<'a, T> {
    /// Construct an empty array list, which will serve as a list
    pub fn new(env: &mut JNIEnv<'a>) -> Result<Self> {
        let obj = env.new_object("java/util/ArrayList", "()V", &[])?;
        Ok(Self {
            obj,
            marker: PhantomData,
            size: 0,
        })
    }

    /// Add an item to the back of the list
    pub fn add(&mut self, env: &mut JNIEnv<'a>, v: T) -> Result<()> {
        let value = JValueGen::Object(v.as_ref());
        env.call_method(&self.obj, "add", "(Ljava/lang/Object;)Z", &[value])?;
        self.size += 1;
        Ok(())
    }

    /// Cast from an untyped java object to this wrapper
    /// We can't check this as I don't see a way to list a class's interfaces
    pub fn cast_unchecked(obj: JObject<'a>, env: &mut JNIEnv<'a>) -> Result<Self> {
        let mut list = Self {
            obj,
            marker: PhantomData,
            size: 0,
        };
        list.size = list.size(env)?;
        Ok(list)
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
            Err(Box::new(InternalJNIError::IndexOutOfBounds {
                len: self.size,
                idx: i,
            }))
        } else {
            T::cast(env, get_object_ref(v)?)
        }
    }

    /// Iterate over the elements in the list
    pub fn iter(&self, env: &mut JNIEnv<'a>) -> Result<impl Iterator<Item = T>> {
        let max = self.size(env)?;
        let mut v = vec![];
        for i in 0..max {
            v.push(self.get(env, i)?);
        }
        Ok(v.into_iter())
    }

    pub fn size(&self, env: &mut JNIEnv<'a>) -> Result<i32> {
        match env.call_method(&self.obj, "size", "()I", &[])? {
            JValueGen::Int(x) => Ok(x),
            v => Err(Box::new(InternalJNIError::BadMemberType {
                expected: "int",
                got: value_type(v),
            })),
        }
    }
}

impl<'a, T> AsRef<JObject<'a>> for List<'a, T> {
    fn as_ref(&self) -> &JObject<'a> {
        &self.obj
    }
}

pub fn jstr_list_to_rust_vec<'a>(
    env: &mut JNIEnv<'a>,
    jlist: &List<'a, JString<'a>>,
) -> Result<Vec<String>> {
    let mut rust_vec = Vec::new();

    for i in 0..jlist.size {
        let element: JString<'a> = jlist.get(env, i)?;
        let j_str = env.get_string(&element)?;
        rust_vec.push(String::from(j_str));
    }

    Ok(rust_vec)
}
