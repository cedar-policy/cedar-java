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
#[cfg(test)]
mod jset_tests {
    use super::*;
    use crate::interface::jvm_based_tests::JVM;
    use jni::objects::JString;

    #[test]
    fn test_add() {
        let mut env = JVM.attach_current_thread().unwrap();
        let mut set = Set::<JString>::new(&mut env).unwrap();
        let test_string = env.new_string("test").unwrap();

        assert!(set.add(&mut env, test_string).is_ok());
        assert_eq!(set.size, 1);
    }

    #[test]
    fn test_as_ref() {
        let mut env = JVM.attach_current_thread().unwrap();
        let set = Set::<JString>::new(&mut env).unwrap();
        let jobject = set.as_ref();

        assert!(!jobject.is_null());
    }
}
