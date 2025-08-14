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
#[cfg(test)]
mod jlist_tests {
    use crate::{
        jlist::{jstr_list_to_rust_vec, List},
        jvm_based_tests::JVM,
    };

    use super::*;
    use jni::objects::JString;

    #[test]
    fn list_new_creates_empty_list() {
        let mut env = JVM.attach_current_thread().unwrap();
        let list = List::<JString>::new(&mut env).unwrap();
        assert_eq!(list.size(&mut env).unwrap(), 0);
    }

    #[test]
    fn list_add_increases_size_and_stores_value() {
        let mut env = JVM.attach_current_thread().unwrap();
        let mut list = List::<JString>::new(&mut env).unwrap();
        let jstr = env.new_string("hello").unwrap();

        list.add(&mut env, jstr).unwrap();
        assert_eq!(list.size(&mut env).unwrap(), 1);

        let val = list.get(&mut env, 0).unwrap();
        let val_str = env.get_string(&val).unwrap();
        assert_eq!(val_str.to_str().unwrap(), "hello");
    }

    #[test]
    fn list_get_out_of_bounds_throws() {
        let mut env = JVM.attach_current_thread().unwrap();
        let mut list = List::<JString>::new(&mut env).unwrap();
        let jstr = env.new_string("item").unwrap();
        list.add(&mut env, jstr).unwrap();

        let result = list.get(&mut env, 1);
        assert!(result.is_err());
        env.exception_clear().unwrap();
    }

    #[test]
    fn list_iterator_returns_all_items_in_order() {
        let mut env = JVM.attach_current_thread().unwrap();
        let mut list = List::<JString>::new(&mut env).unwrap();

        for s in ["one", "two", "three"] {
            let jstr = env.new_string(s).unwrap();
            list.add(&mut env, jstr).unwrap();
        }

        let iterated: Vec<String> = list
            .iter(&mut env)
            .unwrap()
            .map(|jstr| env.get_string(&jstr).unwrap().into())
            .collect();

        assert_eq!(iterated, vec!["one", "two", "three"]);
    }

    #[test]
    fn jstr_list_to_rust_vec_correct_conversion() {
        let mut env = JVM.attach_current_thread().unwrap();
        let mut list = List::<JString>::new(&mut env).unwrap();
        for s in ["a", "b", "c"] {
            let jstr = env.new_string(s).unwrap();
            list.add(&mut env, jstr).unwrap();
        }

        let vec = jstr_list_to_rust_vec(&mut env, &list).unwrap();
        assert_eq!(vec, vec!["a", "b", "c"]);
    }

    #[test]
    fn jstr_list_to_rust_vec_empty() {
        let mut env = JVM.attach_current_thread().unwrap();
        let list = List::<JString>::new(&mut env).unwrap();

        let vec = jstr_list_to_rust_vec(&mut env, &list).unwrap();
        assert!(vec.is_empty());
    }

    #[test]
    fn list_cast_unchecked_from_valid_obj() {
        let mut env = JVM.attach_current_thread().unwrap();
        let obj = env.new_object("java/util/ArrayList", "()V", &[]).unwrap();

        let mut list = List::<JString>::cast_unchecked(obj, &mut env).unwrap();
        assert_eq!(list.size(&mut env).unwrap(), 0);

        let jstr = env.new_string("test").unwrap();
        list.add(&mut env, jstr).unwrap();
        assert_eq!(list.size(&mut env).unwrap(), 1);
    }

    #[test]
    fn as_ref_returns_jobject() {
        let mut env = JVM.attach_current_thread().unwrap();
        let list = List::<JString>::new(&mut env).unwrap();
        let jobject = list.as_ref();

        assert!(!jobject.is_null());
        let jobject2 = list.as_ref();
        assert_eq!(jobject.as_raw(), jobject2.as_raw());
    }
    #[test]
    fn index_out_of_bounds_error() {
        let mut env = JVM.attach_current_thread().unwrap();
        let mut list = List::<JString>::new(&mut env).unwrap();
        let jstr = env.new_string("item").unwrap();
        list.add(&mut env, jstr).unwrap();

        let result = list.get(&mut env, 1);
        let _error_msg = match result {
            Err(ref err) => format!("{}", err),
            Ok(_) => panic!("Expected error, but got Ok"),
        };
        assert!(result.is_err());
        env.exception_clear().unwrap();
    }
    #[test]
    fn size_method_works() {
        let mut env = JVM.attach_current_thread().unwrap();
        let list = List::<JString>::new(&mut env).unwrap();
        assert_eq!(list.size(&mut env).unwrap(), 0);
    }
}
