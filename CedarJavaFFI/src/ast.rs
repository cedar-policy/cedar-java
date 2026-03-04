use cedar_policy_core::{
    ast::{self, ExprVisitor},
    parser,
};
use smol_str::SmolStr;
struct TestVisitor;

impl TestVisitor {
    fn new() -> Self {
        Self {}
    }
}

fn serialise_loc(loc: Option<&parser::Loc>) -> String {
    if loc.is_some() {
        let offset = loc.unwrap().span.offset();
        let len = loc.unwrap().span.len();
        // let file = std::sync::Arc::clone(&loc.unwrap().snippet());
        return format!(
            ", \"source\": {{ \"offset\": {}, \"len\": {} }}",
            offset, len
        );
    }

    String::new()
}

impl ast::ExprVisitor for TestVisitor {
    type Output = String;

    fn visit_var(&mut self, var: ast::Var, loc: Option<&parser::Loc>) -> Option<Self::Output> {
        let src_loc = serialise_loc(loc);
        let fmt = |var: &str| {
            Some(format!(
                "{{ \"type\": \"var\", \"ref\": \"{}\"{} }}",
                var, src_loc
            ))
        };

        match var {
            ast::Var::Principal => fmt("principal"),
            ast::Var::Action => fmt("action"),
            ast::Var::Resource => fmt("resource"),
            ast::Var::Context => fmt("context"),
        }
    }

    fn visit_literal(
        &mut self,
        lit: &ast::Literal,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let src_loc = serialise_loc(loc);

        let fmt = |ty: &str, val: &str| {
            Some(format!(
                "{{ \"type\": \"{}\", \"value\": {}{} }}",
                ty, val, src_loc
            ))
        };

        match lit {
            ast::Literal::Bool(b) => fmt("bool", b.to_string().as_str()),
            ast::Literal::Long(l) => fmt("long", l.to_string().as_str()),
            ast::Literal::String(s) => fmt("str", format!("\"{}\"", s).as_str()),
            ast::Literal::EntityUID(uid) => fmt(
                "euid",
                format!(
                    "\"{}\"",
                    str::replace(uid.to_string().as_str(), "\"", "\\\"")
                )
                .as_str(),
            ),
        }
    }

    /// Visits a slot reference in a policy template.
    fn visit_slot(&mut self, slot: ast::SlotId, loc: Option<&parser::Loc>) -> Option<Self::Output> {
        let src_loc = serialise_loc(loc);
        let fmt = |val: &str| {
            Some(format!(
                "{{ \"type\": \"slot\", \"value\": {}{} }}",
                val, src_loc
            ))
        };

        if slot.is_principal() {
            fmt("principal")
        } else {
            fmt("resource")
        }
    }

    /// Visits an unknown value for partial evaluation
    fn visit_unknown(
        &mut self,
        _unknown: &ast::Unknown,
        _loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        None
    }

    /// Visits an if-then-else conditional expression.
    ///
    /// Recursively visits the condition, then branch, and else branch.
    fn visit_if(
        &mut self,
        test_expr: &std::sync::Arc<ast::Expr>,
        then_expr: &std::sync::Arc<ast::Expr>,
        else_expr: &std::sync::Arc<ast::Expr>,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let condition = self.visit_expr(test_expr)?;
        let then = self.visit_expr(then_expr)?;
        let els = self.visit_expr(else_expr)?;
        let src_loc = serialise_loc(loc);
        Some(format!(
            "{{ \"type\": \"cond\", \"condition\": {}, \"then\": {}, \"else\": {}{} }}",
            condition, then, els, src_loc
        ))
    }

    /// Visits a logical AND expression.
    ///
    /// Recursively visits the left and right operands.
    fn visit_and(
        &mut self,
        left: &std::sync::Arc<ast::Expr>,
        right: &std::sync::Arc<ast::Expr>,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let one = self.visit_expr(left)?;
        let o = format!("{}", one);
        let two = self.visit_expr(right)?;
        let t = format!("{}", two);

        let src_loc = serialise_loc(loc);

        Some(format!(
            "{{ \"type\": \"binary\", \"op\": \"and\", \"left\": {}, \"right\": {}{} }}",
            one, two, src_loc
        ))
    }

    /// Visits a logical OR expression.
    ///
    /// Recursively visits the left and right operands.
    fn visit_or(
        &mut self,
        left: &std::sync::Arc<ast::Expr>,
        right: &std::sync::Arc<ast::Expr>,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let one = self.visit_expr(left)?;
        let two = self.visit_expr(right)?;
        let src_loc = serialise_loc(loc);

        Some(format!(
            "{{ \"type\": \"binary\", \"op\": \"or\", \"left\": {}, \"right\": {}{} }}",
            one, two, src_loc
        ))
    }

    /// Visits a unary operation (like negation).
    ///
    /// Recursively visits the operand.
    fn visit_unary_app(
        &mut self,
        op: ast::UnaryOp,
        arg: &std::sync::Arc<ast::Expr>,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let src_loc = serialise_loc(loc);
        let expr = self.visit_expr(arg)?;
        let exp_str = expr.as_str();

        let fmt = |op: &str, val: &str| {
            Some(format!(
                "{{ \"type\": \"unary\", \"op\": {}, \"expr\": {}{} }}",
                op, val, src_loc
            ))
        };

        match op {
            ast::UnaryOp::IsEmpty => Some(format!(
                "{{ \"type\": \"call\", \"self\": {}, \"func\": \"isEmpty\", \"args\": []{} }}",
                exp_str, src_loc
            )),
            ast::UnaryOp::Neg => fmt("neg", exp_str),
            ast::UnaryOp::Not => fmt("neg", exp_str),
        }
    }

    /// Visits a binary operation (like comparison or arithmetic).
    ///
    /// Recursively visits both operands.
    fn visit_binary_op(
        &mut self,
        op: ast::BinaryOp,
        arg1: &std::sync::Arc<ast::Expr>,
        arg2: &std::sync::Arc<ast::Expr>,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let one = self.visit_expr(arg1)?;
        let two = self.visit_expr(arg2)?;
        let src_loc = serialise_loc(loc);

        let fmt_bin = |op: &str| {
            Some(format!(
                "{{ \"type\": \"binary\", \"op\": \"{}\", \"left\": {}, \"right\": {}{} }}",
                op, one, two, src_loc
            ))
        };
        let fmt_call = |func: &str| {
            Some(format!(
                "{{ \"type\": \"call\", \"self\": \"{}\", \"func\": {}, \"args\": [{}]{} }}",
                one, func, two, src_loc
            ))
        };

        match op {
            ast::BinaryOp::Eq => fmt_bin("eq"),
            ast::BinaryOp::Less => fmt_bin("lt"),
            ast::BinaryOp::LessEq => fmt_bin("leq"),
            ast::BinaryOp::Add => fmt_bin("add"),
            ast::BinaryOp::Sub => fmt_bin("sub"),
            ast::BinaryOp::Mul => fmt_bin("mul"),
            ast::BinaryOp::In => fmt_bin("in"),
            ast::BinaryOp::Contains => fmt_call("contains"),
            ast::BinaryOp::ContainsAll => fmt_call("containsAll"),
            ast::BinaryOp::ContainsAny => fmt_call("containsAny"),
            ast::BinaryOp::GetTag => fmt_call("getTag"),
            ast::BinaryOp::HasTag => fmt_call("hasTag"),
        }
    }

    /// Visits an extension function call (like `ip()`).
    ///
    /// Recursively visits each argument.
    fn visit_extension_function(
        &mut self,
        _fn_name: &ast::Name,
        _args: &std::sync::Arc<Vec<ast::Expr>>,
        _loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        // for arg in args.iter() {
        //     if let Some(output) = self.visit_expr(arg) {
        //         return Some(output);
        //     }
        // }
        None
    }

    /// Visits an attribute access expression (e.g., `expr.attr`).
    ///
    /// Recursively visits the target expression.
    fn visit_get_attr(
        &mut self,
        expr: &std::sync::Arc<ast::Expr>,
        attr: &SmolStr,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let obj = self.visit_expr(expr)?;
        let src_loc = serialise_loc(loc);

        Some(format!(
            "{{ \"type\": \"prop\", \"obj\": {}, \"prop\": \"{}\"{} }}",
            obj, attr, src_loc
        ))
    }

    /// Visits an attribute existence check (e.g., `expr has attr`).
    ///
    /// Recursively visits the target expression.
    fn visit_has_attr(
        &mut self,
        expr: &std::sync::Arc<ast::Expr>,
        attr: &SmolStr,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let obj = self.visit_expr(expr)?;
        let src_loc = serialise_loc(loc);

        Some(format!(
            "{{ \"type\": \"binary\", \"op\": \"has\", \"left\": {}, \"right\": \"{}\"{} }}",
            obj, attr, src_loc
        ))
    }

    /// Visits a pattern-matching expression (e.g., `expr like "pat"`).
    ///
    /// Recursively visits the target expression.
    fn visit_like(
        &mut self,
        expr: &std::sync::Arc<ast::Expr>,
        pattern: &ast::Pattern,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let obj = self.visit_expr(expr)?;
        let src_loc = serialise_loc(loc);

        Some(format!(
            "{{ \"type\": \"binary\", \"op\": \"like\", \"left\": {}, \"right\": \"{}\"{} }}",
            obj, pattern, src_loc
        ))
    }

    /// Visits a type-checking expression (e.g., `principal is User`).
    ///
    /// Recursively visits the target expression.
    fn visit_is(
        &mut self,
        expr: &std::sync::Arc<ast::Expr>,
        entity_type: &ast::EntityType,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let obj = self.visit_expr(expr)?;
        let src_loc = serialise_loc(loc);

        Some(format!("{{ \"type\": \"binary\", \"op\": \"is\", \"left\": {}, \"right\": {{ \"type\": \"type\", \"value\": \"{}\"{} }}{} }}", obj, entity_type.name().to_string(), src_loc, src_loc))
    }

    /// Visits a set literal expression (e.g., `[1, 2, 3]`).
    ///
    /// Recursively visits each element in the set.
    fn visit_set(
        &mut self,
        elements: &std::sync::Arc<Vec<ast::Expr>>,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let mut elems = String::new();
        let mut sep = "";
        let src_loc = serialise_loc(loc);

        for element in elements.iter() {
            if let Some(output) = self.visit_expr(element) {
                elems.push_str(sep);
                elems.push_str(output.as_str());
                sep = ", ";
            }
        }
        Some(format!(
            "{{ \"type\": \"set\", \"elements\": [{}]{} }}",
            elems, src_loc
        ))
    }

    /// Visits a record literal expression (e.g., `{ "key": value }`).
    ///
    /// Recursively visits the value of each field in the record.
    fn visit_record(
        &mut self,
        fields: &std::sync::Arc<std::collections::BTreeMap<SmolStr, ast::Expr>>,
        loc: Option<&parser::Loc>,
    ) -> Option<Self::Output> {
        let mut props = String::new();
        let mut sep = "";
        let src_loc = serialise_loc(loc);

        for prop in fields.keys() {
            let expr = fields.get(prop)?;
            if let Some(output) = self.visit_expr(expr) {
                props.push_str(sep);
                props.push_str("\"");
                props.push_str(prop);
                props.push_str("\": ");
                props.push_str(output.as_str());
                sep = ", ";
            }
        }
        Some(format!(
            "{{ \"type\": \"record\", \"props\": {{ {} }}{} }}",
            props, src_loc
        ))
    }
}

fn serialise_annotations(policy: &ast::Policy) -> String {
    let mut result = String::new();

    if policy.annotations().next().is_some() {
        result.push_str(", \"annotations\": { ");
        let mut sep = "";
        for annotation in policy.annotations() {
            result.push_str(sep);
            result.push('"');
            result.push_str(annotation.0.clone().into_smolstr().as_str());
            result.push_str("\": \"");
            result.push_str(annotation.1.val.as_str());
            result.push('"');
            sep = ", ";
        }
        result.push_str(" }");
    }

    return result;
}

pub fn parse_policy_set_to_ast(
    text: &str,
) -> std::result::Result<String, parser::err::ParseErrors> {
    let ast: ast::PolicySet = parser::parse_policyset(&text)?;

    let mut result = String::new();
    result.push('[');

    let mut sep = "";

    for policy in ast.policies() {
        let mut visitor = TestVisitor::new();
        let expr = policy.condition();
        let s = format!("{}", expr);
        let condition = visitor.visit_expr(&expr).expect("error parsing expression");
        result.push_str(sep);
        let annotations = serialise_annotations(policy);

        let source = serialise_loc(policy.loc());

        result.push_str(&format!(
            "{{ \"effect\": \"{}\", \"condition\": {}{}{} }}",
            policy.effect(),
            condition,
            annotations,
            source
        ));
        sep = ", ";
    }
    result.push(']');

    Ok(result)
}

#[cfg(test)]
mod tests {
    use crate::ast::*;

    #[test]
    fn test_permit_all() {
        let result = parse_policy_set_to_ast("permit (principal, action, resource);");
        assert!(
            result.is_ok(),
            "Expected parse_policy_set_to_ast to succeed: {:?}",
            result
        );
    }

    #[test]
    fn test_with_euid() {
        let result = parse_policy_set_to_ast(
            r#"permit (
                principal,
                action,
                resource
            )
            when { principal == Namespace::Nested::"euid" };"#,
        );
        assert!(
            result.is_ok(),
            "Expected parse_policy_set_to_ast to succeed: {:?}",
            result
        );

        assert!(result
            .unwrap()
            .as_str()
            .contains("\"value\": \"Namespace::Nested::\\\"euid\\\"\""));
    }

    #[test]
    fn test_annotations() {
        let result = parse_policy_set_to_ast(
            r#"
            @annotation_name("annotation value")
            @second_annotation("valuable info")
            permit (
                principal,
                action,
                resource
            );"#,
        );
        assert!(
            result.is_ok(),
            "Expected parse_policy_set_to_ast to succeed: {:?}",
            result
        );

        assert!(result.unwrap().as_str().contains("\"annotations\": { \"annotation_name\": \"annotation value\", \"second_annotation\": \"valuable info\" }"));
    }
}
