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

use std::{marker::PhantomData};

use crate::{objects::Object, utils::Result};
use jni::{
    objects::{JObject, JValueGen},
    JNIEnv,
};

/// Typed wrapper for Java maps
/// (java.util.Map)
#[derive(Debug)]
pub struct Map<'a, T, U> {
    /// Underlying Java object
    obj: JObject<'a>,
    /// ZST for tracking key type info
    key_marker: PhantomData<T>,
    /// ZST for tracking value type info
    value_marker: PhantomData<U>,
}

impl<'a, T: Object<'a>, U: Object<'a>> Map<'a, T, U> {
    /// Construct an empty hash map, which will serve as a semap
    pub fn new(env: &mut JNIEnv<'a>) -> Result<Self> {
        let obj = env.new_object("java/util/HashMap", "()V", &[])?;

        Ok(Self {
            obj,
            key_marker: PhantomData,
            value_marker: PhantomData,
        })
    }

    /// Get a value mapped to a key
    pub fn get(&mut self, env: &mut JNIEnv<'a>, k: T) -> Result<JObject<'a>> {
        let key = JValueGen::Object(k.as_ref());
        let key = env
            .call_method(
                &self.obj,
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                &[key],
            )?
            .l()?;
        Ok(key)
    }

    /// Put a key-value pair to the map
    pub fn put(&mut self, env: &mut JNIEnv<'a>, k: T, v: U) -> Result<JObject<'a>> {
        let key = JValueGen::Object(k.as_ref());
        let value = JValueGen::Object(v.as_ref());
        let value = env
            .call_method(
                &self.obj,
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                &[key, value],
            )?
            .l()?;
        Ok(value)
    }
}

impl<'a, T, U> AsRef<JObject<'a>> for Map<'a, T, U> {
    fn as_ref(&self) -> &JObject<'a> {
        &self.obj
    }
}
